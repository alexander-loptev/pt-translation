<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="/">
  <html>
  <head>
  <style>
    td:not(.penn), p{
      font-size: 120%;
    }
  </style>
  </head>
  <body>
  <h3>Original text</h3>
  <p><xsl:value-of select="text-translation/original-text"/></p>
  <xsl:for-each select="text-translation/translation">
      <h3>Translator engine: <xsl:value-of select="@engine"/></h3>
      <p><xsl:value-of select="translated-text"/></p>
      <xsl:for-each select="sentence">
          <table border="1">
            <tr bgcolor="#9acd32">
              <td colspan="2"><b><xsl:value-of select="translated-sentence"/></b></td>
            </tr>
            <tr>
                <td colspan="2" class="penn"><pre><xsl:value-of select="sentence-penn-string"/></pre></td>
            </tr>
            <tr>
                <td colspan="2">&#160;</td>
            </tr>
            <xsl:for-each select="phrase">
                <xsl:sort select="string-length(original)" order="ascending" data-type="number"/>
                <tr>
                    <xsl:choose>
                        <xsl:when test="@meaningful = 1">
                            <td bgcolor="#99ff99"><b><xsl:value-of select="translated-phrase"/></b></td><td bgcolor="#99ff99">Meaningful</td>
                        </xsl:when>
                        <xsl:otherwise>
                            <td bgcolor="#ff9999"><b><xsl:value-of select="translated-phrase"/></b></td><td bgcolor="#ff9999">Meaningless</td>
                        </xsl:otherwise>
                    </xsl:choose>
                </tr>
                <xsl:for-each select="suggestion">
                    <xsl:sort select="@relative-score" order="descending"/>
                    <tr>
                      <td><xsl:value-of select="self::node()"/></td>
                      <td><xsl:value-of select="@relative-score"/></td>
                    </tr>
                </xsl:for-each>
                <tr><td>&#160;</td><td>&#160;</td></tr>
            </xsl:for-each>
          </table>
      </xsl:for-each>
  </xsl:for-each>
  </body>
  </html>
</xsl:template>

</xsl:stylesheet>
