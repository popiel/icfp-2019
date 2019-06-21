package com.wolfskeep

import scala.util.parsing.combinator._

object Entry {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      for (task <- scala.io.Source.stdin.getLines) { process(task) }
    } else {
      for {
        name <- args
        task <- scala.io.Source.fromFile(name).getLines
      } { process(task, Some(name)) }
    }
  }

  def process(prob: String, name: Option[String] = None) {
    val out = name.map(n => new java.io.PrintStream(n.replace("problems/", "solutions/").replace(".desc", ".sol"))).getOrElse(System.out)
    out.println(ProblemParser(prob))
  }
}

case class Point(x: Int, y: Int) {
  override def toString = s"($x,$y)"
}
case class Booster(code: Char, where: Point) {
  override def toString = s"$code$where"
}
case class Problem(map: Seq[Point], start: Point, obstacles: Seq[Seq[Point]], boosters: Seq[Booster])

object ProblemParser extends RegexParsers {
  def num: Parser[Int] = """\d+""".r ^^ { _.toInt }
  def point: Parser[Point] = ("(" ~> num <~ ",") ~ num <~ ")" ^^ { case x ~ y => Point(x, y) }
  def map = repsep(point, ",")
  def code: Parser[Char] = """B|F|L|X|R""".r ^^ { _.head }
  def booster = code ~ point ^^ { case c ~ p => Booster(c, p) }
  def obstacles = repsep(map, ";")
  def boosters = repsep(booster, ";")
  def problem = (map <~ "#") ~ (point <~ "#") ~ (obstacles <~ "#") ~ boosters ^^ { case m ~ p ~ o ~ b => Problem(m, p, o, b) }

  def apply(input: String): Problem = parseAll(problem, input) match {
    case Success(result, _) => result
    case failure: NoSuccess => scala.sys.error(failure.msg)
  }
}

