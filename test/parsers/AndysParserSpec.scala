package parsers

import org.scalatest.{FlatSpec, Matchers}

class AndysParserSpec extends FlatSpec with Matchers {

  it should "parse the confirmation page" in {
    val html = scala.io.Source.fromInputStream(ClassLoader.getSystemResourceAsStream("step4.html")).getLines().mkString("\n")

    println {
      AndysParser.parseConfirmationPage(html, "ru")
    }
  }
}
