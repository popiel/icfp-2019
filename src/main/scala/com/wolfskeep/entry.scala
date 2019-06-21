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
    val bot = Bot()
    val pos = mine.toPos(problem.start)
    println(mine)
  }
}

case class State(pos: Int, bot: Bot, mine: Mine) {
  def expire(time: Int) = {
    val b2 = bot.expire(time)
    if (bot eq b2) this else copy(bot = b2)
  }

  def move(offset: Int) = {
    val p1 = pos + offset
    val d = mine.cells.applyOrElse(p1, (_: Int) => '!').toUpper
    if (d == '!' || (d == '#' && bot.drillingUntil == 0)) this
    else {
      val isGrabbable = d.isLetter && d != 'X' && d != 'T'
      val m2 = if (d == '#' || isGrabbable) mine.updated(p1, '*') else mine
      val b2 = if (isGrabbable) bot.copy(inventory = bot.inventory.updated(d, bot.inventory.getOrElse(d, 0) + 1)) else bot
      State(p1, b2, m2.paint(p1, b2))
    }
  }
}

case class Path(state: State, from: Option[(Action, Path)]) {
  lazy val repr: String = from match {
    case Some((a, p)) => p.toString + a.toString
    case None => ""
  }
  override def toString = repr

  def bot = state.bot
  def pos = state.pos
  def mine = state.mine
  lazy val time: Int = from.map(_._2.time + 1).getOrElse(0)

  def apply(action: Action): Path = action match {
    case Wait if !bot.onTimers => this
    case Wait => Path(state.expire(time + 1), Some(action, this))
    case RotateCW  => Path(state.copy(bot = bot.rotateCW .expire(time + 1), mine = mine.paint(pos, bot.rotateCW)),  Some(action, this))
    case RotateCCW => Path(state.copy(bot = bot.rotateCCW.expire(time + 1), mine = mine.paint(pos, bot.rotateCCW)), Some(action, this))
    case GoFast => Path(state.copy(bot = bot.startFast(time)), Some(action, this))
    case StartDrilling => Path(state.copy(bot = bot.startDrilling(time)), Some(action, this))
    case AttachArm(x, y) =>
      val b = bot.attachArm(x, y)
      Path(state.copy(bot = b.expire(time + 1), mine = mine.paint(pos, b)), Some(action, this))
    case x: Move =>
      val offset = mine.toOffset(x.offset)
      val s = if (bot.fastUntil != 0) state.move(offset).move(offset).expire(time + 1) else state.move(offset).expire(time + 1)
      Path(s, Some(action, this))
    case Reset => bot.plant match {
      case None => this
      case Some(b) => Path(state.copy(bot = b.expire(time + 1), mine = mine.updated(pos, 't')), Some(action, this))
    }
    case Shift => 
  }
}

sealed trait Action
sealed trait Move {
  def offset: Point
}
object MoveUp              extends Move with Action { override def toString = "W"; def offset = Point( 0,  1) }
object MoveDown            extends Move with Action { override def toString = "S"; def offset = Point( 0, -1) }
object MoveRight           extends Move with Action { override def toString = "D"; def offset = Point( 1,  0) }
object MoveLeft            extends Move with Action { override def toString = "A"; def offset = Point(-1,  0) }
object Wait                          extends Action { override def toString = "Z" }
object RotateCW                      extends Action { override def toString = "E" }
object RotateCCW                     extends Action { override def toString = "Q" }
case class AttachArm(x: Int, y: Int) extends Action { override def toString = s"B($x,$y)" }
object GoFast                        extends Action { override def toString = "F" }
object StartDrilling                 extends Action { override def toString = "L" }
object Reset                         extends Action { override def toString = "R" }
case class Shift(x: Int, y: Int)     extends Action { override def toString = s"T($x,$y)" }

case class Mine(startX: Int, startY: Int, width: Int, height: Int, cells: IndexedSeq[Char]) {
  override def toString = {
    val o = s"(${startX + width},${startY + height})"
    s"""${" "*(width - o.length max 0)}$o
${cells.grouped(width).map(_.mkString).toVector.reverse.mkString("\n")}
($startX,$startY)"""
  }

  def toPos(p: Point) = (p.y - startY) * width + p.x - startX
  def toOffset(p: Point) = (p.y) * width + p.x
  def paint(where: Point, bot: Bot): Mine = paint(toPos(where), bot)
  def paint(where: Int, bot: Bot): Mine = {
    copy(cells = (where +: bot.arms.map(toOffset(_) + where)).filter(o => o >= 0 && o < cells.length).foldLeft(cells)((c, o) =>
      c.updated(o, c(o) match {
        case '.' => '*'
        case x => x.toLower
      })
    ))
  }

  def finished = !cells.exists(c => c == '.' || c.isUpper)

  def updated(pos: Int, c: Char) = copy(cells = cells.updated(pos, c))
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
      ).toVector :+ '!'
    }).flatten
    val width = maxX - minX + 1
    val height = maxY - minY
    val withBoosters = problem.boosters.foldLeft(cells)((c, b) =>
      c.updated((b.where.y - minY) * width + b.where.x - minX, b.code)
    )
    Mine(minX, minY, width, height, withBoosters)
  }
}

case class Bot(arms: Seq[Point] = Seq(Point(1, 1), Point(1, 0), Point(1, -1)), fastUntil: Int = 0, drillingUntil: Int = 0, inventory: Map[Char, Int] = Map.empty[Char, Int]) {
  def rotateCW = copy(arms = arms.map(_.rotateCW))
  def rotateCCW = copy(arms = arms.map(_.rotateCCW))
  def onTimers = fastUntil > 0 || drillingUntil > 0
  def expire(time: Int) = {
    val fast  = if (fastUntil     <= time) this.copy(fastUntil     = 0) else this
    val drill = if (drillingUntil <= time) fast.copy(drillingUntil = 0) else fast
    drill
  }

  def use(code: Char): Option[Map[Char, Int]] = {
    if (inventory.getOrElse(code, 0) == 0) None
    else Some(inventory.updated(code, inventory(code) - 1))
  }

  def startDrilling(now: Int) =
    use('L') match {
      case None => this
      case Some(inv) => copy(drillingUntil = now + 30, inventory = inv).expire(now + 1)
    }

  def startFast(now: Int) =
    use('F') match {
      case None => this
      case Some(inv) => copy(fastUntil = now + 50, inventory = inv).expire(now + 1)
    }

  def attachArm(x: Int, y: Int) =
    use('B') match {
      case None => this
      case Some(inv) =>
        if ((Point(0, 0) +: arms).exists(p => (p.x == x || p.y == y) && (p.x - x + p.y - y).abs == 1) &&
            !arms.contains(Point(x, y)))
          copy(arms = Point(x, y) +: arms, inventory = inv)
        else
          this
    }

  def plant = use('R').map(inv => copy(inventory = inv))
}

case class Point(x: Int, y: Int) {
  override def toString = s"($x,$y)"
  def rotateCW = Point(y, -x)
  def rotateCCW = Point(-y, x)
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

