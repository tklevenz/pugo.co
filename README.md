Pugo - Google Docs Export
=============================

This project is part of a paper for my M.CSc at Trier University of Applied Science.

## What is it?
A webservice that provides a way of exporting a Google Doc to any XML or Text based format that can be generated with XSLT.

## How does it work?
It takes a Google Docs html export and transforms it into wellformed xhtml that can then be transformed into anything that can be generated with XSLT.

## Installation
The project uses maven, so the best way to run and compile it is using maven, however it can also easily be imported into Eclipse or Intellij and run from there.

To run locally using maven: `mvn tomcat7:run`

To compile a war: `mvn package`

The compiled war can be deployed on Tomcat 7 and 8

## Usage
To use send a GET request to http://server/war_name/convert with the following Parameters:

**source** = https://docs.google.com/feeds/download/documents/export/Export?id=docid&exportFormat=html

**token** = OAuth token for the file

**fname** = filename of the resulting Output

**mode (optional)** = mode for selecting which transformation to run, by default it does html (when mode parameter is ommitted), **md** for markdown and **epub**

Additionally any number of Parameters of the form **xslParam_\<Parameter Name>** can be send and will be passed to the XSLT transformation as \<Parameter Name>

There is a bunch of ways to get an export link of a google doc and an OAauth token, one example using the Google Picker API check out [script.js](../src/main/webapp/script.js) which is used in the ePub Demo

## Configuration
In the webapp directory are config files of the follwoing form that can be added or adjusted:

```xml
<config>
    <!-- XSLT Stylesheet used for the transformation -->
    <xsl>html.xsl</xsl>
    <!-- Extension of Output file -->
    <outputExt>html</outputExt>
    <!-- HttpServletResponse.setContentType -->
    <mimeType>text/html</mimeType>
    <!--
        If true files generated with xsl:result-document
        will be written to a zip file,
        standard output will be discarded
    -->
    <zipOutput>false</zipOutput>
</config>
```

Additional config files should be named config_**\<mode>**.xml and can be used using the mode parameter.

## Demo
http://pugo.co/pugo-co-1.11/


