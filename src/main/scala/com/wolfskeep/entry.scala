package com.wolfskeep

import scala.util.parsing.combinator._
import scala.collection.immutable.Vector

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
    val out = name.map(n => new java.io.PrintStream(n.replace(".desc", ".sol"))).getOrElse(System.out)
    val problem = ProblemParser(prob)
    val mine = Mine(problem)
    println(mine)
  }
}

case class Mine(startX: Int, startY: Int, width: Int, height: Int, cells: IndexedSeq[Char]) {
  override def toString = {
    val o = s"(${startX + width},${startY + height})"
    s"""${" "*(width - o.length max 0)}$o
${cells.grouped(width).map(_.mkString).toVector.reverse.mkString("\n")}
($startX,$startY)"""
  }
}
object Mine {
  def isVert(p: Seq[Point]) = p(0).x == p(1).x
  def apply(problem: Problem): Mine = {
    val minX = problem.border.map(_.x).min
    val minY = problem.border.map(_.y).min
    val maxX = problem.border.map(_.x).max
    val maxY = problem.border.map(_.y).max
    val segs = ((problem.border.last +: problem.border sliding 2) ++ (problem.obstacles.flatMap(o => o.last +: o sliding 2)))
      .filter(isVert)
      .map(s => (s(0).x, s(0).y, s(1).y))
      .toList.sorted
    val cells = (for {
      y <- minY until maxY
      crossings = segs.filter(s => s._2 <= y && s._3 > y || s._3 <= y && s._2 > y)
    } yield {
      ((minX +: crossings.map(_._1) :+ maxX) sliding (3, 2)).flatMap(pp =>
        (if (pp.size > 1) Vector.fill(pp(1) - pp(0))('#') else Vector.empty[Char]) ++
        (if (pp.size > 2) Vector.fill(pp(2) - pp(1))('.') else Vector.empty[Char])
      )
    }).flatten
    val width = maxX - minX
    val height = maxY - minY
    val withBoosters = problem.boosters.foldLeft(cells)((c, b) =>
      c.updated((b.where.y - minY) * width + b.where.x - minX, b.code)
    )
    Mine(minX, minY, width, height, withBoosters)
  }
}

case class Point(x: Int, y: Int) {
  override def toString = s"($x,$y)"
}
case class Booster(code: Char, where: Point) {
  override def toString = s"$code$where"
}
case class Problem(border: Seq[Point], start: Point, obstacles: Seq[Seq[Point]], boosters: Seq[Booster])

object ProblemParser extends RegexParsers {
  def num: Parser[Int] = """\d+""".r ^^ { _.toInt }
  def point: Parser[Point] = ("(" ~> num <~ ",") ~ num <~ ")" ^^ { case x ~ y => Point(x, y) }
  def map = rep1sep(point, ",")
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

