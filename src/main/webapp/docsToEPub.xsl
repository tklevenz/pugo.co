<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="http://www.w3.org/1999/xhtml" xpath-default-namespace="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs" version="2.0">

    <xsl:variable name="title" select="/html/head/title"/>

    <xsl:param name="pub-language" select="'en'"/>
    <xsl:param name="bookid" select="concat('http://pugo.co/', replace($title, ' ', ''))"/>


    <xsl:variable name="chapters">
        <xsl:for-each-group select="/html/body/*[not(tokenize(@class, ' ') = 'title')]"
            group-starting-with="h1">
            <chapter>
                <xsl:copy-of select="current-group()"/>
            </chapter>
        </xsl:for-each-group>
    </xsl:variable>


    <xsl:template match="/">
        <!-- mimetype -->
        <xsl:result-document href="mimetype" method="text"
            >application/epub+zip</xsl:result-document>

        <!-- container -->
        <xsl:result-document href="META-INF/container.xml">
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                <rootfiles>
                    <rootfile full-path="EPUB/content.opf"
                        media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        </xsl:result-document>

        <!-- content.opf -->
        <xsl:result-document href="EPUB/content.opf">
            <package xmlns="http://www.idpf.org/2007/opf"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:dcterms="http://purl.org/dc/terms/" version="3.0" xml:lang="en"
                unique-identifier="bookid">
                <metadata>
                    <dc:title>
                        <xsl:value-of select="$title"/>
                    </dc:title>
                    <dc:language id="pub-language">
                        <xsl:value-of select="$pub-language"/>
                    </dc:language>
                    <dc:identifier id="bookid">
                        <xsl:value-of select="$bookid"/>
                    </dc:identifier>
                    <meta property="dcterms:modified">
                        <xsl:value-of select="current-dateTime()"/>
                    </meta>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="htmltoc" properties="nav" media-type="application/xhtml+xml"
                        href="toc.xhtml"/>
                    <item id="front_cover" href="front-cover.xhtml"
                        media-type="application/xhtml+xml"/>
                    <xsl:for-each select="$chapters/*">
                        <item id="chapter{position()}" href="chapter{position()}.xhtml"
                            media-type="application/xhtml+xml"/>
                    </xsl:for-each>
                    <item id="style" href="stylesheet.css" media-type="text/css"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="front_cover"/>
                    <xsl:for-each select="$chapters/*">
                        <itemref idref="chapter{position()}"/>
                    </xsl:for-each>
                </spine>
            </package>
        </xsl:result-document>

        <!-- front-cover.xhtml -->
        <xsl:result-document href="EPUB/front-cover.xhtml" method="xhtml">
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <link rel="stylesheet" type="text/css" href="stylesheet.css"/>
                    <title>
                        <xsl:value-of select="$title"/>
                    </title>
                </head>
                <body>
                    <h1>
                        <xsl:value-of select="$title"/>
                    </h1>
                    <h2>Created with pugo.co</h2>
                </body>
            </html>
        </xsl:result-document>

        <!-- stylesheet.css -->
        <xsl:result-document href="EPUB/stylesheet.css" method="text">
            <xsl:variable name="regex">(.*?)\{(.*?)\}</xsl:variable>
            <xsl:analyze-string select="/html/head/style/text()" regex="{$regex}">
                <xsl:matching-substring>
                    <xsl:value-of select="regex-group(1)"/>
                    <xsl:text> {&#10;</xsl:text>
                    <xsl:for-each select="tokenize(regex-group(2), ';')">
                        <xsl:text>    </xsl:text>
                        <xsl:value-of select="."/>
                        <xsl:text>;&#10;</xsl:text>
                    </xsl:for-each>
                    <xsl:text>}&#10;</xsl:text>
                </xsl:matching-substring>
            </xsl:analyze-string>
        </xsl:result-document>

        <!-- chapters -->
        <xsl:for-each select="$chapters/*">
            <xsl:result-document href="EPUB/chapter{position()}.xhtml" method="xhtml">
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <link rel="stylesheet" type="text/css" href="stylesheet.css"/>
                        <title>
                            <xsl:value-of select="h1"/>
                        </title>
                    </head>
                    <body>
                        <xsl:apply-templates select="*"/>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>

        <!-- toc.ncx -->
        <xsl:result-document href="EPUB/toc.ncx">
            <ncx xmlns:ncx="http://www.daisy.org/z3986/2005/ncx/"
                xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1" xml:lang="en">
                <head>
                    <meta name="dtb:uid" content="http://example.org/dummy/URIstub/"/>
                    <meta name="dtb:depth" content="1"/>
                    <meta name="dtb:totalPageCount" content="0"/>
                    <meta name="dtb:maxPageNumber" content="0"/>
                </head>
                <docTitle>
                    <text/>
                </docTitle>
                <navMap>
                    <navPoint id="front_cover" playOrder="1">
                        <navLabel>
                            <text>
                                <xsl:value-of select="$title"/>
                            </text>
                        </navLabel>
                        <content src="front-cover.xhtml"/>
                    </navPoint>
                    <xsl:for-each select="$chapters/*">
                        <navPoint id="chapter{position()}" playOrder="2">
                            <navLabel>
                                <text>
                                    <xsl:value-of select="h1"/>
                                </text>
                            </navLabel>
                            <content src="chapter{position()}.xhtml"/>
                        </navPoint>
                    </xsl:for-each>
                </navMap>
            </ncx>
        </xsl:result-document>

        <!-- toc.xhtml -->
        <xsl:result-document href="EPUB/toc.xhtml" method="xhtml">
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head>
                    <link rel="stylesheet" type="text/css" href="stylesheet.css"/>
                </head>
                <body>
                    <nav epub:type="toc" id="toc">
                        <ol>
                            <li>
                                <a href="front-cover.xhtml">
                                    <xsl:value-of select="$title"/>
                                </a>
                            </li>
                            <xsl:for-each select="$chapters/*">
                                <li>
                                    <a href="chapter{position()}.xhtml">
                                        <xsl:value-of select="h1"/>
                                    </a>
                                </li>
                            </xsl:for-each>
                        </ol>
                    </nav>
                </body>
            </html>
        </xsl:result-document>
    </xsl:template>

    <xsl:template match="node() | @*">
        <xsl:copy>
            <xsl:apply-templates select="node() | @*"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="img">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:attribute name="src" select="concat('images/', @alt)"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
