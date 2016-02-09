// The Browser API key obtained from the Google Developers Console.
var developerKey = 'AIzaSyCHy_B7FYOaOMeG-2-grtZdsoARTmTZO3Q';

// The Client ID obtained from the Google Developers Console. Replace with your own Client ID.
var clientId = "136953074513-i7d6p1a26ni6uapq9md64nr1obu7mp9m.apps.googleusercontent.com"

// Scope to use to access user's documents.
var scope = ['https://www.googleapis.com/auth/drive.readonly'];

var pickerApiLoaded = false;
var oauthToken;

// Use the API Loader script to load google.picker and gapi.auth.
function onApiLoad() {
    document.getElementById('pick').addEventListener('click', authAndSetupPicker);
}

function authAndSetupPicker() {
    gapi.load('auth', {
        'callback': onAuthApiLoad
    });
    gapi.load('picker', {
        'callback': onPickerApiLoad
    });
}

function onAuthApiLoad() {
    window.gapi.auth.authorize({
            'client_id': clientId,
            'scope': scope,
            'immediate': false
        },
        handleAuthResult);
}

function onPickerApiLoad() {
    pickerApiLoaded = true;
    createPicker();
}

function handleAuthResult(authResult) {
    if (authResult && !authResult.error) {
        oauthToken = authResult.access_token;
        console.log("expires_in: " + authResult.expires_in);
        createPicker();
    }
}

// Create and render a Picker object for picking user documents.
function createPicker() {
    if (pickerApiLoaded && oauthToken) {
        var picker = new google.picker.PickerBuilder().
        addView(google.picker.ViewId.DOCUMENTS).
        setOAuthToken(oauthToken).
        setDeveloperKey(developerKey).
        setCallback(pickerCallback).
        build();
        picker.setVisible(true);
    }
}

/**
 * Download a file's content.
 *
 * @param {File} file Drive File instance.
 * @param {Function} callback Function to call when the request is complete.
 */
function exportFile(file, callback) {
    if (file['exportLinks']['text/html']) {
        var accessToken = gapi.auth.getToken().access_token;
        var xhr = new XMLHttpRequest();
        var exportLink = encodeURI(file['exportLinks']['text/html']);
        document.getElementById('result').innerHTML = 
                'Title: ' + file['title'] +
                '<br>html exportLink: ' + exportLink + 
                '<br>accessToken: ' + accessToken;

            // setup form
            document.getElementById('source').value = exportLink;
            document.getElementById('token').value = accessToken;
            document.getElementById('fname').value = encodeURI(file['title']);
            document.getElementById('run_transform').disabled = false;
    } else {
        callback(null);
    }
}

// A simple callback implementation.
function pickerCallback(data) {
    var url = 'nothing';
    if (data[google.picker.Response.ACTION] == google.picker.Action.PICKED) {
        var doc = data[google.picker.Response.DOCUMENTS][0];
        url = doc[google.picker.Document.URL];
    }

    if (data.action === google.picker.Action.PICKED) {
        var id = data.docs[0].id;
        console.log(id);
        var request = new XMLHttpRequest();
        request.open('GET', 'https://www.googleapis.com/drive/v2/files/' + id);
        request.setRequestHeader('Authorization', 'Bearer ' + oauthToken);

        request.addEventListener('load', function() {
            var item = JSON.parse(request.responseText);
            console.log(item);

            exportFile(item, function(responseText) {
                console.log(responseText);
            });
        });

        request.send();
    }
}
