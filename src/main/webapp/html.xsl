<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs"
    version="2.0">
    
    <xsl:output encoding="utf-8" method="xhtml"/>
    
    <!-- 
        copy template will pass through the document and copy all elements 
        unless another template applies to the element 
    -->
    <xsl:template match="node() | @*">
        <xsl:copy>
            <xsl:apply-templates select="node() | @*"/>
        </xsl:copy>
    </xsl:template>
    
    
    <!-- EXAMPLE TEMPLATES, UNCOMMENT TO USE -->
    
    <!-- add additional div inside body -->
    <!--
    <xsl:template match="body">
        <xsl:copy>
            <div id="main">
              <xsl:apply-templates select="node() | @*"/>  
            </div>
        </xsl:copy>
    </xsl:template>
    -->
    
    <!-- add additional css to output -->
    <!--
    <xsl:template match="style">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            #main {
                margin: 0 auto;
                width: 800px;
            }
        </xsl:copy>        
    </xsl:template>
    -->
    
    <!-- adds a class 'header' and 'h1' or 'h2' -->
    <!--
    <xsl:template match="h1 | h2">
        <xsl:copy>
            <xsl:apply-templates select="node() | @*"/>
            <xsl:attribute name="class" select="@class,'header', name(.)" separator=" "/>
        </xsl:copy>
    </xsl:template>
    -->
    
    
</xsl:stylesheet>