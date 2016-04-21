<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:pfn="http://pugo.co/functions" xmlns="http://www.w3.org/1999/xhtml" xpath-default-namespace="http://www.w3.org/1999/xhtml" xml:space="preserve" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs" version="2.0">
<xsl:output method="text" encoding="UTF-8"/>
    
<!-- global vars -->
<xsl:variable name="break">    
</xsl:variable>
    
<xsl:variable name="divider">
----
</xsl:variable>
    
<xsl:variable name="textStart"><xsl:value-of select="$divider"/>Text:<xsl:value-of select="$break, $break"/></xsl:variable>
    
<xsl:variable name="quoteStyle">color:#F0AC57 style:center</xsl:variable>    
    
<xsl:variable name="localDomain">foodtrucks-deutschland.de</xsl:variable>
    
<xsl:variable name="domainPattern" select="concat('(http|https)://(www\.)?', $localDomain)"/>
    
<xsl:variable name="css"><xsl:copy-of select="/html/head/style[@type = 'text/css']/node()"/></xsl:variable>
    


    <!-- functions -->
    <xsl:function
        name="pfn:processLink" xml:space="default">
        <xsl:param name="link"/>
        <xsl:variable name="link"
            select="
                if (starts-with($link, 'https://www.google.com/url?q=')) then
                    substring-after($link, 'https://www.google.com/url?q=')
                else
                    $link"/>
        <xsl:value-of select="replace($link, $domainPattern, '')"/>
    </xsl:function>


    <xsl:function name="pfn:checkFontStyle" xml:space="default"
        as="xs:boolean">
        <xsl:param name="class"/>
        <xsl:param name="style"/>
        <xsl:variable name="cssClass"
            select="substring-before(substring-after($css, concat('.', $class, '{')), '}')"/>
        <xsl:value-of select="matches($cssClass, concat('font-weight:', $style))"/>
    </xsl:function>
    


    <!-- main -->
    <xsl:template match="/"><xsl:apply-templates select="html/body"/></xsl:template>

    <xsl:template
        match="body" xml:space="default">
        <xsl:apply-templates select="table" mode="meta"/>
        <xsl:if test="hr">
            <xsl:apply-templates
                select="(h1 | p)[following-sibling::hr][not(preceding-sibling::hr)][span]"
                mode="meta"/>
        </xsl:if>
        <xsl:value-of select="$textStart"/>
        <xsl:apply-templates
            select="
                if (hr) then
                    *[preceding-sibling::hr]
                else
                    *[preceding-sibling::table]"
        />
    </xsl:template>
    
    

    <!-- meta -->
    <!-- first table in doc will hold meta information -->
    <xsl:template match="body/table[not(preceding-sibling::table)]" mode="meta"><xsl:apply-templates select=".//tr" mode="meta"/></xsl:template>
    
    <xsl:template match="tr" mode="meta"><xsl:apply-templates select="td[1]//span"/>: <xsl:apply-templates select="td[2]//span"/><xsl:value-of select="$divider"/></xsl:template>
    
    <!-- Headline -->
    <xsl:template match="h1" mode="meta">Headline: <xsl:apply-templates select=".//span"/><xsl:value-of select="$divider"/></xsl:template>
    
    <!-- Teaser -->
    <xsl:template match="p" mode="meta"><xsl:if test="not(preceding-sibling::p[span])">Teaser: </xsl:if><xsl:apply-templates select="."/></xsl:template>
    
    
    
    <!-- text -->
    <!-- delete empty elements -->
    <xsl:template match="*[not(node())][not(self::br)]"/>
    
    <!-- paras/text -->
    <xsl:template match="p"><xsl:apply-templates select="span"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <xsl:template match="span"><xsl:apply-templates select="node()"/></xsl:template>
        
    <xsl:template match="
            span[for $i in tokenize(@class, ' ')
            return
                pfn:checkFontStyle(@class, 'bold')]">**<xsl:apply-templates select="node()"/>**</xsl:template>
        
    <xsl:template match="
            span[for $i in tokenize(@class, ' ')
            return
                pfn:checkFontStyle(@class, 'italic')]">_<xsl:apply-templates select="node()"/>_</xsl:template>
        
    <xsl:template match="span/text()"><xsl:value-of select="normalize-space(.)"/></xsl:template>
        
    <xsl:template match="br"><xsl:value-of select="$break"/></xsl:template>    
    
    <xsl:template match="a">(link:<xsl:value-of select="pfn:processLink(@href)"/> text:<xsl:value-of select="normalize-space(.)"/>)</xsl:template>
    
    <!-- headings -->
    <xsl:template match="h1"># <xsl:apply-templates select="node()"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <xsl:template match="h2">## <xsl:apply-templates select="node()"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <xsl:template match="h3">### <xsl:apply-templates select="node()"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <xsl:template match="h4">#### <xsl:apply-templates select="node()"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <xsl:template match="h5">##### <xsl:apply-templates select="node()"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <!-- lists -->
    <xsl:template match="ul | ol"><xsl:apply-templates select="li"/><xsl:value-of select="$break, $break"/></xsl:template>
    
    <xsl:template match="ul/li">- <xsl:apply-templates select="node()"/><xsl:value-of select="$break"/></xsl:template>
    
    <xsl:template match="ol/li"><xsl:value-of select="position()"/>. <xsl:apply-templates select="node()"/><xsl:value-of select="$break"/></xsl:template>
    
    <!-- quote -->
    <xsl:template match="p[preceding-sibling::*[1][self::hr]][following-sibling::*[1][self::hr]]">(quote:<xsl:apply-templates select="span"/> <xsl:value-of select="$quoteStyle"/>)<xsl:value-of select="$break, $break"/></xsl:template>    


</xsl:stylesheet>
