package parsers

import models.MenuItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import scala.util.matching.Regex
import scala.collection.JavaConverters._

object AndysParser {

  private def parseId(elem: Element): Long =
    elem.select(".ands_buy").attr("onclick") match {
      case R.addtocartRegex(id)    => id.toLong
      case R.showorderwinRegex(id) => id.toLong
    }

  private def parseName(elem: Element): String = elem.select(".descr .title").text

  private def parsePrice(elem: Element): String = elem.select(".ands_price").text

  private def parseIngredients(elem: Element): List[String] =
    elem.select(".title").asScala.head.nextElementSibling().text.split(",\\s").toList

  private def parseImage(elem: Element): List[String] = Nil

  def parsePizzaPage(html: String, lang: String): List[MenuItem] = {
    val doc = Jsoup.parse(html)

    doc.select(".ands_p_buy").asScala
      .map { elem =>
        val id = parseId(elem)
        val name = parseName(elem)
        val price = parsePrice(elem)
        val ingredients = parseIngredients(elem)
        val images = parseImage(elem)

        MenuItem(id, "pizza", name, price, ingredients, images)
      }
      .toList
  }

  def parseConfirmationPage(html: String, lang: String): List[(String, String)] = {
    val doc = Jsoup.parse(html)

    doc.select(".b_total1 tr").asScala.map { elem =>
      val key = elem.select("th").text().stripSuffix(":").trim
      val value = elem.select("td").text()

      key -> value
    }.toList
  }

  private object R {

    val addtocartRegex: Regex = raw"addtocart\((\d+),\d+\);".r

    val showorderwinRegex: Regex = raw"showorderwin\((\d+)\);".r
  }
}
