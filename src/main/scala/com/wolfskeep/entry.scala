package com.wolfskeep

import scala.collection.immutable.Vector
import scala.util.parsing.combinator._
import scala.util.Try

object Entry {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      for (task <- scala.io.Source.stdin.getLines) { process(task) }
    } else {
      for {
        name <- args
        task <- scala.io.Source.fromFile(name).getLines
      } {
        println(name)
        process(task, Some(name))
      }
    }
  }

  def process(prob: String, name: Option[String] = None) {
    val problem = ProblemParser(prob)
    val mine = Mine(problem)
    val bot = Bot()
    val pos = mine.toPos(problem.start)
    val state = State(pos, bot, mine.paint(pos, bot))

    // println(state.mine)
    println(s"${name.getOrElse("")} - (${mine.width},${mine.height}) - ${problem.start}")

    val open = scala.collection.mutable.Stack(Path(state, None))
    var closed = Set(state)
    while (open.nonEmpty) {
      val work = open.pop()
      if (work.finished) {
        val out = name.map(n => new java.io.PrintStream(n.replace(".desc", ".sol"))).getOrElse(System.out)
        out.println(work)
        out.close()
        // println(work.mine)
        name match {
          case Some(n) =>
            val sName = n.replace(".desc", ".sol").replace("problems", "solutions")
            val best = Try(scala.io.Source.fromFile(sName).getLines.mkString).getOrElse("").filter(_.isLetter).length
            val cost = work.toString.filter(_.isLetter).length
            if (best == 0 || best > cost) {
              println(s"$n: Improving from $best to $cost")
              Try {
                val f = new java.io.PrintStream(sName);
                f.println(work)
                f.close()
              }
            } else {
              println(s"$n: Prior $best remains better than $cost")
            }
          case _ =>
        }
        open.clear()
      } else {
        val next = work.children.filter(p => !closed(p.state))
        // val next = Seq(work(work.mine.moveToUnpainted(work.pos)))
        closed ++= next.map(_.state)
        open.pushAll(next)
      }
    }
  }
}

case class State(pos: Int, bot: Bot, mine: Mine) {
  def expire(time: Int) = {
    val b2 = bot.expire(time)
    if (bot eq b2) this else copy(bot = b2)
  }

  def move(offset: Int) = {
    val p1 = pos + offset
    val d = mine(p1).toUpper
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
  lazy val repr: String = {
    val sb = new StringBuilder()
    var p = this
    while (p.from != None) {
      sb.append(p.from.get._1.toString.reverse)
      p = p.from.get._2
    }
    sb.toString.reverse
  }
  override def toString = repr

  def bot = state.bot
  def pos = state.pos
  def mine = state.mine
  def finished = mine.finished
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
    case Shift(x, y) =>
      val p2 = mine.toPos(Point(x, y))
      if (p2 == pos || mine.cells(p2) != 't') this
      else Path(state.copy(bot = bot.expire(time + 1), pos = p2, mine = mine.paint(p2, bot)), Some(action, this))
  }

  def legal(action: Action) = action match {
    case Wait => bot.onTimers
    case RotateCW  => true
    case RotateCCW => true
    case GoFast => bot.inventory.getOrElse('F', 0) > 0
    case StartDrilling => bot.inventory.getOrElse('L', 0) > 0
    case AttachArm(x, y) => bot.inventory.getOrElse('B', 0) > 0
    case x: Move =>
      val offset = mine.toOffset(x.offset)
      val p2 = pos + offset
      p2 >= 0 && p2 < mine.cells.length && mine(p2) != '!' && (bot.drillingUntil > 0 || mine(p2) != '#')
    case Reset => bot.inventory.getOrElse('R', 0) > 0 && mine(pos) == '*'
    case Shift(x, y) => mine(mine.toPos(Point(x, y))) == 't'
  }

  val strongSort = true
/*
  def children = {
    val moves = Seq[Move](MoveUp, MoveDown, MoveLeft, MoveRight).filter(legal)
    if (!strongSort) {
      val m2 = moves.map(apply).sortBy(_.mine eq mine)
      if (m2.exists(_.mine ne mine)) m2
      else m2.sortBy {
        case Path(_, Some((a: Move, _))) => 
          val offset = mine.toOffset(a.offset)
          val p2 = pos + offset
          mine.routeToUnpainted(p2)
        case _ => Int.MaxValue
      }
    } else {
      moves.sortBy { a =>
        val offset = mine.toOffset(a.offset)
        val p2 = pos + offset
        mine.routeToUnpainted(p2)
      }.map(apply)
    }
  }
*/
  def children: Seq[Path] = {
    (
      (for {
        m <- Seq(MoveLeft, MoveUp, MoveRight, MoveDown)
        if mine.grabbable(pos + mine.toOffset(m.offset))
      } yield m) ++
      (if (bot.inventory.getOrElse('B', 0) > 0) Seq(AttachArm(bot)) else Seq.empty[Action]) ++
      (if (bot.fastUntil == 0 && bot.inventory.getOrElse('F', 0) > 0) Seq(GoFast) else Seq.empty[Action]) ++
      Seq(mine.moveToUnpainted(pos))
    ).distinct.map(apply).reverse
  }
}

sealed trait Action
sealed trait Move extends Action {
  def offset: Point
}
object MoveUp                        extends Move   { override def toString = "W"; def offset = Point( 0,  1) }
object MoveDown                      extends Move   { override def toString = "S"; def offset = Point( 0, -1) }
object MoveRight                     extends Move   { override def toString = "D"; def offset = Point( 1,  0) }
object MoveLeft                      extends Move   { override def toString = "A"; def offset = Point(-1,  0) }
object Wait                          extends Action { override def toString = "Z" }
object RotateCW                      extends Action { override def toString = "E" }
object RotateCCW                     extends Action { override def toString = "Q" }
case class AttachArm(x: Int, y: Int) extends Action { override def toString = s"B($x,$y)" }
object GoFast                        extends Action { override def toString = "F" }
object StartDrilling                 extends Action { override def toString = "L" }
object Reset                         extends Action { override def toString = "R" }
case class Shift(x: Int, y: Int)     extends Action { override def toString = s"T($x,$y)" }

object AttachArm {
  def apply(bot: Bot): AttachArm = {
    val u = bot.arms.find(a => (a.x + a.y).abs == 1).get
    val l = bot.arms.length - 1
    AttachArm(u.x * l, u.y * l)
  }
}

case class Mine(startX: Int, startY: Int, width: Int, height: Int, cells: IndexedSeq[Char]) {
  override def toString = {
    val o = s"(${startX + width},${startY + height})"
    s"""${" "*(width - o.length max 0)}$o
${cells.grouped(width).map(_.mkString).toVector.reverse.mkString("\n")}
($startX,$startY) - (${startX + width},${startY + height})"""
  }

  def toPos(p: Point) = (p.y - startY) * width + p.x - startX
  def toOffset(p: Point) = (p.y) * width + p.x
  def paint(where: Point, bot: Bot): Mine = paint(toPos(where), bot)
  def paint(where: Int, bot: Bot): Mine = {
    val reachable = where +: bot.arms.map(toOffset(_) + where).takeWhile(canMoveTo)
    if (reachable.exists(unpainted))
      copy(cells = reachable.foldLeft(cells)((c, o) =>
        c.updated(o, c(o) match {
          case '.' => '*'
          case x => x.toLower
        })
      ))
    else this
  }

  def finished = !cells.exists(c => c == '.' || c.isUpper)

  def apply(pos: Int) = if (pos < 0 || pos >= cells.length) '!' else cells(pos)
  def unpainted(pos: Int) = {
    val d = apply(pos)
    d == '.' || d.isUpper
  }
  def canMoveTo(pos: Int) = {
    val d = apply(pos)
    d != '#' && d != '!'
  }
  def grabbable(pos: Int) = {
    val d = apply(pos).toUpper
    d.isLetter && d != 'X' && d != 'T'
  }

  def updated(pos: Int, c: Char) = copy(cells = cells.updated(pos, c))

  private[this] def findRoutes(): Array[Int] = {
    val cost = Array.fill(cells.length)(Int.MaxValue)
    for (p <- 0 until cells.length) {
      if (unpainted(p)) cost(p) = 0
    }
    var progressing = true
    while (progressing) {
      progressing = false
      for {
        p1 <- (0 until cells.length) ++ (cells.length - 1 to 0 by -1)
        if canMoveTo(p1)
        o <- Seq(-width, -1, 1, width)
        p2 = p1 + o
        if p2 >= 0 && p2 < cells.length
        if cost(p1) > cost(p2) + 1
      } {
        cost(p1) = cost(p2) + 1
        progressing = true
      }
    }
    cost
  }

  lazy val routeToUnpainted = findRoutes()

  def moveToUnpainted(pos: Int): Action = {
    if (unpainted(pos-1))     return MoveLeft
    if (unpainted(pos+width)) return MoveUp
    if (unpainted(pos+1))     return MoveRight
    if (unpainted(pos-width)) return MoveDown

    val cost = scala.collection.mutable.Map[Int, Int]().withDefaultValue(Int.MaxValue)
    val open = scala.collection.mutable.Queue[Int]()
    cost(pos) = 0;
    for { (o, k) <- Seq(-1, width, 1, -width).zipWithIndex } {
      val p = pos + o
      if (canMoveTo(p)) {
        cost(p) = 4 + k
        open.enqueue(p)
      }
    }
    while (open.nonEmpty) {
      val p = open.dequeue()
      for (o <- Seq(-width, -1, 1, width)) {
        val p2 = p + o
        if (unpainted(p2)) {
          return Seq(MoveLeft, MoveUp, MoveRight, MoveDown)(cost(p) & 3)
        }
        if (canMoveTo(p2) && cost(p2) > cost(p) + 4) {
          cost(p2) = cost(p) + 4
          open.enqueue(p2)
        }
      }
    }
    return Wait
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

case class Bot(arms: Seq[Point] = Seq(Point(1, 1), Point(1, -1), Point(1, 0)), fastUntil: Int = 0, drillingUntil: Int = 0, inventory: Map[Char, Int] = Map.empty[Char, Int]) {
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
      case Some(inv) => copy(drillingUntil = now + 31, inventory = inv).expire(now + 1)
    }

  def startFast(now: Int) =
    use('F') match {
      case None => this
      case Some(inv) => copy(fastUntil = now + 51, inventory = inv).expire(now + 1)
    }

  def attachArm(x: Int, y: Int) =
    use('B') match {
      case None => this
      case Some(inv) =>
        if ((Point(0, 0) +: arms).exists(p => (p.x == x || p.y == y) && (p.x - x + p.y - y).abs == 1) &&
            !arms.contains(Point(x, y)))
          copy(arms = arms :+ Point(x, y), inventory = inv)
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
  def code: Parser[Char] = """B|F|L|X|R|C""".r ^^ { _.head }
  def booster = code ~ point ^^ { case c ~ p => Booster(c, p) }
  def obstacles = repsep(map, ";")
  def boosters = repsep(booster, ";")
  def problem = (map <~ "#") ~ (point <~ "#") ~ (obstacles <~ "#") ~ boosters ^^ { case m ~ p ~ o ~ b => Problem(m, p, o, b) }

  def apply(input: String): Problem = parseAll(problem, input) match {
    case Success(result, _) => result
    case failure: NoSuccess => scala.sys.error(failure.msg)
  }
}

