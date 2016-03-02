/*
    The MIT License (MIT)
    
    Copyright (c) 2016 Tobias Klevenz (tobias.klevenz@gmail.com)
    
    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

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
    gapi.client.setApiKey(developerKey);
    window.setTimeout(checkAuth, 1);
}

function checkAuth() {
    gapi.auth.authorize({
        client_id: clientId,
        scope: scope,
        immediate: true
    }, handleAuthResult);
}

function handleAuthResult(authResult) {
    var pickButton = document.getElementById('pick');
    if (authResult && !authResult.error) {
        oauthToken = authResult.access_token;
        pickButton.onclick = createPicker;
        loadPicker();
    } else {
        pickButton.onclick = handleAuthClick;
    }
}

function handleAuthClick(event) {
    gapi.auth.authorize({
        client_id: clientId,
        scope: scope,
        immediate: false
    }, handleAuthResult);
    return false;
}

function loadPicker() {
    gapi.load('picker', {
        'callback': onPickerApiLoad
    });
}

function onPickerApiLoad() {
    pickerApiLoaded = true;
    createPicker();
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
 * Setup hidden form param fields
 * Enable transform button and transformation params dialog
 *
 * @param {File} file Drive File instance.
 */
function setupTransform(file) {
    if (file['exportLinks']['text/html']) {
        var accessToken = gapi.auth.getToken().access_token;
        var exportLink = encodeURI(file['exportLinks']['text/html']);

        // setup form
        document.getElementById('source').value = exportLink;
        document.getElementById('token').value = accessToken;
        document.getElementById('fname').value = encodeURI(file['title']);
        document.getElementById('run_transform').disabled = false;
        document.getElementById('transform_params').style.display = "block";
    } else {
        console.log(file);
    }
}

// get file Drive instance from picker
function pickerCallback(data) {
    if (data.action === google.picker.Action.PICKED) {
        var id = data.docs[0].id;
        var request = new XMLHttpRequest();
        request.open('GET', 'https://www.googleapis.com/drive/v2/files/' + id);
        request.setRequestHeader('Authorization', 'Bearer ' + oauthToken);

        request.addEventListener('load', function() {
            var file = JSON.parse(request.responseText);
            console.log(file);

            setupTransform(file);
        });

        request.send();
    }
}
