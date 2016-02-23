<?xml version="1.0" encoding="UTF-8"?>
<!-- 
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
 -->
 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="http://www.w3.org/1999/xhtml" xpath-default-namespace="http://www.w3.org/1999/xhtml"
    xmlns:epub="http://www.idpf.org/2007/ops" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:pfn="http://pugo.co/functions" exclude-result-prefixes="xs" version="2.0">

    <xsl:variable name="title" select="(/html/body/*[tokenize(@class, ' ') = 'title'])[1]//text()"/>

    <xsl:param name="pub-language" select="'en'"/>
    <xsl:param name="book-id" select="concat('http://pugo.co/', replace($title, ' ', ''))"/>
    <xsl:param name="grouping-level" as="xs:integer" select="2"/>


    <!-- 
        Creating hierarchical structure from flat html to a temporary variable
        By default this will group into chapters and subchapters starting with each h1, h2 and h3
    -->
    <xsl:variable name="chapters">
        <!-- 
            h1
        -->
        <xsl:for-each-group select="/html/body/*[not(tokenize(@class, ' ') = 'title')]"
            group-starting-with="h1[.//text()]">
            <chapter>
                <xsl:copy-of select="current-group()[1]/@*"/>
                <section
                    epub:type="{if (current-group()[1]/self::h1) then 'chapter' else 'preface'}">
                    <xsl:choose>
                        <!-- 
                            h2
                        -->
                        <xsl:when test="$grouping-level >= 2">
                            <xsl:variable name="current-group">
                                <xsl:copy-of select="current-group()"/>
                            </xsl:variable>
                            <xsl:copy-of
                                select="$current-group/*[not(self::h2 | preceding-sibling::h2)]"/>
                            <xsl:for-each-group
                                select="$current-group/*[self::h2 | preceding-sibling::h2]"
                                group-starting-with="h2[.//text()]">
                                <section epub:type="subchapter">
                                    <xsl:choose>
                                        <!-- 
                                            h3
                                        -->
                                        <xsl:when test="$grouping-level >= 3">
                                            <xsl:variable name="current-group">
                                                <xsl:copy-of select="current-group()"/>
                                            </xsl:variable>
                                            <xsl:copy-of
                                                select="$current-group/*[not(self::h3 | preceding-sibling::h3)]"/>
                                            <xsl:for-each-group
                                                select="$current-group/*[self::h3 | preceding-sibling::h3]"
                                                group-starting-with="h3[.//text()]">
                                                <section epub:type="subchapter">
                                                  <xsl:copy-of select="current-group()"/>
                                                </section>
                                            </xsl:for-each-group>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:copy-of select="current-group()"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </section>
                            </xsl:for-each-group>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:copy-of select="current-group()"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </section>
            </chapter>
        </xsl:for-each-group>
    </xsl:variable>


    <!-- main template -->
    <xsl:template match="/">

        <xsl:variable name="default_title" select="'ePub generated by Pugo.co'"/>
        <xsl:variable name="title"
            select="
                if ($title != '') then
                    $title
                else
                    $default_title"/>

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
                        <xsl:value-of select="$book-id"/>
                    </dc:identifier>
                    <meta property="dcterms:modified">
                        <xsl:value-of
                            select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01]T[H]:[m]:[s]Z')"
                        />
                    </meta>
                </metadata>
                <manifest>
                    <!--<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>-->
                    <item id="htmltoc" properties="nav" media-type="application/xhtml+xml"
                        href="toc.xhtml"/>
                    <item id="front_cover" href="front-cover.xhtml"
                        media-type="application/xhtml+xml"/>
                    <xsl:for-each select="$chapters/*">
                        <item id="{pfn:generateItemID(section)}"
                            href="{pfn:generateFileName(section)}.xhtml"
                            media-type="application/xhtml+xml"/>
                    </xsl:for-each>
                    <!--<xsl:for-each-group select="$chapters//img" group-by="@src">
                        <xsl:variable name="name"
                            select="
                                if (@alt = '') then
                                    concat('image', count(preceding::img[@alt = '']) + 1, '.png')
                                else
                                    @alt"/>
                        <item id="{$name}" href="images/{$name}"
                            media-type="image/{replace(tokenize($name,'\.')[last()],'jpg','jpeg','i')}"
                        />
                    </xsl:for-each-group>-->
                    <item id="style" href="stylesheet.css" media-type="text/css"/>
                </manifest>
                <spine>
                    <itemref idref="front_cover"/>
                    <xsl:for-each select="$chapters/*">
                        <itemref idref="{pfn:generateItemID(section)}"/>
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
                    <xsl:if test="$title = $default_title">
                        <p>No Title was found in your Google Doc, please set a Heading as
                            'Title'.</p>
                    </xsl:if>
                </body>
            </html>
        </xsl:result-document>

        <!-- stylesheet.css -->
        <xsl:result-document href="EPUB/stylesheet.css" method="text">
            <xsl:variable name="regex">(.*?)\{(.*?)\}</xsl:variable>
            <xsl:analyze-string select="/html/head/style/text()" regex="{$regex}">
                <xsl:matching-substring>
                    <xsl:if test="not(matches(regex-group(1), 'import'))">
                        <xsl:value-of select="regex-group(1)"/>
                        <xsl:text> {&#10;</xsl:text>
                        <xsl:for-each select="tokenize(regex-group(2), ';')">
                            <xsl:if test="not(matches(., 'direction:'))">
                                <xsl:text>    </xsl:text>
                                <xsl:value-of select="."/>
                                <xsl:text>;&#10;</xsl:text>
                            </xsl:if>
                        </xsl:for-each>
                        <xsl:text>}&#10;</xsl:text>
                    </xsl:if>
                </xsl:matching-substring>
            </xsl:analyze-string>
        </xsl:result-document>


        <!-- chapters -->
        <xsl:apply-templates select="$chapters/*"/>


        <!-- toc.ncx 
            
            toc.ncx is needed for epub 2.0 compatibility
            there could be problems with a nested structure and certain devices:
            http://epubsecrets.com/nesting-your-toc-in-the-ncx-file-and-the-nookkindle-workaround.php
            
            
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
                        <navPoint id="chapter{position()}" playOrder="{position()+1}">
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
        </xsl:result-document>-->

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
                            <xsl:apply-templates select="$chapters/*" mode="toc"/>
                        </ol>
                    </nav>
                </body>
            </html>
        </xsl:result-document>
    </xsl:template>

    <xsl:template match="chapter">
        <xsl:result-document href="EPUB/{pfn:generateFileName(section)}.xhtml" method="xhtml">
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <link rel="stylesheet" type="text/css" href="stylesheet.css"/>
                    <title>
                        <xsl:value-of select="section/h1"/>
                    </title>
                </head>
                <body>
                    <xsl:apply-templates select="section"/>
                </body>
            </html>
        </xsl:result-document>
    </xsl:template>



    <xsl:template match="section">
        <section>
            <xsl:copy-of select="@*"/>
            <xsl:attribute name="title" select="h1 | h2 | h3"/>
            <xsl:attribute name="id" select="pfn:generateChapterID(.)"/>
            <xsl:apply-templates select="*"/>
        </section>
    </xsl:template>




    <xsl:template match="section" mode="toc">
        <xsl:variable name="fileName" select="pfn:generateFileName(ancestor::chapter/section)"/>
        <xsl:variable name="subchapter"
            select="
                if (@epub:type = 'subchapter') then
                    concat('#', pfn:generateChapterID(.))
                else
                    ''"/>
        <li>
            <a href="{$fileName}.xhtml{$subchapter}">
                <xsl:variable name="heading" select="h1 | h2 | h3"/>
                <xsl:value-of select="$heading[1] | .[not($heading != '')]/@epub:type"/>
            </a>
            <xsl:if test="section[@epub:type = 'subchapter']">
                <ol>
                    <xsl:apply-templates select="section" mode="toc"/>
                </ol>
            </xsl:if>
        </li>
    </xsl:template>

    <xsl:template match="node() | @*">
        <xsl:copy>
            <xsl:apply-templates select="node() | @*"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="a[@href]">
        <xsl:variable name="parentFilename" select="pfn:generateFileName(ancestor::chapter/section)"/>
        <xsl:variable name="href" select="@href"/>
        <xsl:variable name="hrefFilename"
            select="pfn:generateFileName($chapters/(chapter[.//*[@id = replace($href, '^#', '')]])[1]/section)"/>
        <a>
            <xsl:apply-templates select="@*"/>
            <xsl:attribute name="href">
                <xsl:value-of
                    select="
                        if ($parentFilename = $hrefFilename or not(starts-with($href, '#'))) then
                            $href
                        else
                            concat($hrefFilename, '.xhtml', $href)"
                />
            </xsl:attribute>
            <xsl:copy-of select="node()"/>
        </a>
    </xsl:template>

    <xsl:template match="@shape | @name | hr[matches(@style, 'page-break')]"/>


    <!-- functions -->
    <xsl:function name="pfn:generateChapterID">
        <xsl:param name="section"/>
        <xsl:variable name="chapterNumber"
            select="count($section/preceding::section[@epub:type = 'chapter']) + 1"/>
        <xsl:variable name="subchapterNumber">
            <xsl:value-of
                select="
                    $chapterNumber,
                    for $i in $section/ancestor-or-self::section[@epub:type = 'subchapter']
                    return
                        count($i | $i/preceding-sibling::section)"
                separator="."/>
        </xsl:variable>
        <xsl:value-of select="concat('c', $subchapterNumber)"/>
    </xsl:function>

    <xsl:function name="pfn:generateFileName">
        <xsl:param name="section"/>
        <xsl:variable name="chapter" select="$section/parent::chapter"/>
        <xsl:variable name="name"
            select="replace(replace(replace(lower-case($section/h1[1]), '[^(a-z|\s)]', ''), '^\s+', ''), '\s+', '-')"/>
        <xsl:value-of
            select="
                format-number(count($chapter/preceding-sibling::chapter) + 1, '000'),
                $name[. != '']"
            separator="-"/>
    </xsl:function>

    <xsl:function name="pfn:generateItemID">
        <xsl:param name="section"/>
        <xsl:value-of select="concat('id', replace(pfn:generateFileName($section), '-', ''))"/>
    </xsl:function>



</xsl:stylesheet>
