package com.wolfskeep

import scala.annotation.tailrec
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

    var solutions = List.empty[Path]
    val open = scala.collection.mutable.Stack(Path(state, None))
    var closed = Map(state -> 0).withDefaultValue(Int.MaxValue)
    while (open.nonEmpty) {
      val work = open.pop()
      if (work.finished) {
        solutions = work +: solutions
        open.clear()
        if (work.bot.inventory.getOrElse('R', 0) > 0) open.pushAll(work.installTeleporter)
      } else {
        val next = work.children.filter(p => closed(p.state) > p.toList.length)
        closed ++= next.map(p => p.state -> p.toList.length)
        open.pushAll(next)
        if (next.isEmpty) {
          // println(work.mine)
          // println(s"bot at ${work.mine.toPoint(work.pos)}")
        }
      }
    }

    if (solutions.nonEmpty) {
      val work = solutions.sortBy(_.toString.count(_.isLetter)).head
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
    }
  }
}

case class State(pos: Int, bot: Bot, mine: Mine, wall: Option[Int] = None, dir: Move = MoveUp, noWalls: Boolean = false) {
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
      copy(pos = p1, bot = b2, mine = m2.paint(p1, b2))
    }
  }

  def pickWall: State = {
    if (wall != None || noWalls) return this
    val cost = scala.collection.mutable.Map[Int, Int]().withDefaultValue(Int.MaxValue)
    val open = scala.collection.mutable.Queue[Int]()
    cost(pos) = 0;
    open.enqueue(pos);
    while (open.nonEmpty) {
      val p = open.dequeue()
      for (o <- Seq(MoveDown, MoveLeft, MoveUp, MoveRight)) {
        val p2 = p + mine.toOffset(o.offset)
        if (mine.unpainted(p) && !mine.canMoveTo(p2)) {
          // println(s"Picking wall ${mine.toPoint(p2)} ${o.rotateCW.rotateCW}")
          return copy(wall = Some(p2), dir = o.rotateCW.rotateCW)
        }
        if (mine.canMoveTo(p2)) {
          if (cost(p2) > cost(p) + 1) {
            cost(p2) = cost(p) + 1
            open.enqueue(p2)
          }
        }
      }
    }
    // println(s"No walls to pick")
    return copy(noWalls = true)
  }

  def walkWall(n: Int = bot.arms.length * 3): State = {
    if (n == 0) {
      // println(s"Clearing wall")
      return copy(wall = None)
    }
    if (wall == None) return pickWall
    val w = wall.get
    // println(s"Walking wall ${mine.toPoint(pos)} $n: ${mine.toPoint(w)} $dir")
    if (mine.unpainted(w + mine.toOffset(dir.offset))) return this
    val rightCorner = w + mine.toOffset(dir.offset + dir.rotateCCW.offset)
    if (!mine.canMoveTo(rightCorner)) return copy(wall = Some(rightCorner), dir = dir.rotateCW).walkWall(n - 1)
    val straight = w + mine.toOffset(dir.rotateCCW.offset)
    if (!mine.canMoveTo(straight)) return copy(wall = Some(straight)).walkWall(n - 1)
    return copy(dir = dir.rotateCCW).walkWall(n - 1)
  }
}

object Path {
  def apply(state: State, from: Option[(Action, Path)]): Path = {
    from match {
      case None => Path(state, from, 0)
      case Some((_, Path(prior, _, n))) => Path(state, from, (if (state.mine eq prior.mine) n + 1 else 0))
    }
  }
  @tailrec private[Path] def toListAcc(p: Path, acc: List[Path]): List[Path] = if (p.from == None) p :: acc else toListAcc(p.from.get._2, p :: acc)
}
case class Path(state: State, from: Option[(Action, Path)], timeSinceChange: Int) {
  val toList: List[Path] = from match {
    case None => List(this)
    case Some((_, prior)) => this :: prior.toList
  }

  lazy val repr: String = {
    val sb = new StringBuilder()
    var p = this
    while (p.from != None) {
      sb.append(p.from.get._1.toString.reverse)
      p = p.from.get._2
    }
    sb.toString.reverse
  }
  override def toString = toList.reverse.tail.view.map(_.from.get._1.toString).mkString

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
      // println(s"Moving ${mine.toPoint(pos)} $x")
      val offset = mine.toOffset(x.offset)
      val s = if (bot.fastUntil != 0) state.move(offset).move(offset).expire(time + 1) else state.move(offset).expire(time + 1)
      if ((s.mine eq mine) && mine.portCost(s.pos)._1 < timeSinceChange) {
        var where = this
        while (where.timeSinceChange > 0 && !where.from.get._1.isInstanceOf[Shift]) where = where.from.get._2
        if (where.from.get._1.isInstanceOf[Shift]) Path(s, Some(action, this))
        else {
          // println(s"On the way to ${mine.toPoint(s.pos)} with time $timeSinceChange > ${mine.portCost(s.pos)._1}")
          where(mine.portCost(s.pos)._2)
        }
      } else Path(s, Some(action, this))
    case Reset => bot.plant match {
      case None => this
      case Some(b) => Path(state.copy(bot = b.expire(time + 1), mine = mine.plant(pos)), Some(action, this))
    }
    case Shift(x, y) =>
      // println(s"Shifting to ($x,$y)")
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

  val moveOrder = Seq(MoveLeft, MoveUp, MoveRight, MoveDown)
  def children: Seq[Path] = {
    val s = state.walkWall()
    val p = copy(state = s)
    (
      (for {
        m <- moveOrder
        if mine.grabbable(pos + mine.toOffset(m.offset))
      } yield m) ++
      (if (bot.inventory.getOrElse('B', 0) > 0) Seq(AttachArm(bot)) else Seq.empty[Action]) ++
      (if (bot.fastUntil == 0 && bot.inventory.getOrElse('F', 0) > 0) Seq(GoFast) else Seq.empty[Action]) ++
      mine.moveToBooster(pos) ++
      p.seekWall ++
      followWall(mine.moveToUnpainted(pos, bot))
      // hugWall
    ).distinct.map(p.apply).reverse
  }

  def seekWall: Option[Action] = {
    if (state.wall == None) return None
    val w = state.wall.get
    if (bot.arms.length > 3) {
      if (bot.arms(2) * -1 != state.dir.offset) {
        if (bot.arms(2).rotateCW == state.dir.offset) {
          if (mine.countToPaint(pos, bot.rotateCCW) > 0) return Some(RotateCCW)
        } else if (bot.arms(2).rotateCCW == state.dir.offset) {
          if (mine.countToPaint(pos, bot.rotateCW) > 0) return Some(RotateCW)
        } else {
          if (mine.countToPaint(pos, bot.rotateCCW) > 0) return Some(RotateCCW)
          if (mine.countToPaint(pos, bot.rotateCW) > 0) return Some(RotateCW)
          if (mine.countToPaint(pos, bot.rotateCW.rotateCW) > 0) return Some(RotateCW)
        }
      }
    } else {
      if (bot.arms(2).rotateCW != state.dir.offset) {
        if (bot.arms(2) == state.dir.offset) {
          if (mine.countToPaint(pos, bot.rotateCCW) > 0) return Some(RotateCCW)
        } else if (bot.arms(2) * -1 == state.dir.offset) {
          if (mine.countToPaint(pos, bot.rotateCW) > 0) return Some(RotateCW)
        } else {
          if (mine.countToPaint(pos, bot.rotateCCW) > 0) return Some(RotateCCW)
          if (mine.countToPaint(pos, bot.rotateCW) > 0) return Some(RotateCW)
          if (mine.countToPaint(pos, bot.rotateCW.rotateCW) > 0) return Some(RotateCW)
        }
      }
    }
    val tip = mine.reachable(pos, bot).last
    val folded = tip != pos + mine.toOffset(bot.arms.last)
    val p = w + mine.toOffset(state.dir)
    val forwMove = state.dir.rotateCCW
    val forwPos = pos + mine.toOffset(forwMove)
    val forwPos2 = forwPos + mine.toOffset(forwMove)
    val outMove = forwMove.rotateCW
    val outPos = pos + mine.toOffset(outMove)
    val outPos2 = outPos + mine.toOffset(forwMove)
    val outPos3 = outPos2 + mine.toOffset(forwMove)
    if (folded) {
      // println(s"folded: p${mine.toPoint(p)} pos${mine.toPoint(pos)} forwMove $forwMove outMove $outMove reachable ${mine.reachable(outPos2, bot).map(mine.toPoint)}")
    }
    if (mine.canMoveTo(outPos) && mine.canMoveTo(outPos2)) {
      if (mine.reachable(outPos2, bot).contains(p)) return Some(outMove)
      if (mine.canMoveTo(outPos3) && mine.reachable(outPos3, bot).contains(p)) return Some(outMove)
    }
    if (mine.canMoveTo(forwPos)) {
      if (p == mine.reachable(forwPos, bot).last) return Some(forwMove)
      if (mine.canMoveTo(forwPos2) && mine.reachable(forwPos2, bot).contains(p)) return Some(forwMove)
    }
    return mine.moveToPos(pos, p, state.dir)
  }

  def hugWall: Seq[Action] = {
    val dirToBrush = Seq(MoveLeft, MoveUp, MoveRight, MoveDown).find(_.offset == bot.arms(2)).get
    if (bot.arms.length >= 3) {
      val dirToWall = dirToBrush
      val tip = mine.reachable(pos, bot).last
      val folded = tip != pos + mine.toOffset(bot.arms.last)
      if (mine.unpainted(tip + mine.toOffset(dirToWall))) {
        if (!mine.canMoveTo(pos + mine.toOffset(dirToWall.rotateCCW))) Seq(RotateCCW, dirToWall)
        else Seq(dirToWall)
      } else {
        val moveForw = dirToWall.rotateCW
        val moveAway = moveForw.rotateCW
        val forw = pos + mine.toOffset(moveForw)
        
        if (mine.canMoveTo(forw)) {
          if (folded && mine.canMoveTo(pos + mine.toOffset(moveAway)) && !mine.reachable(forw, bot).contains(tip + mine.toOffset(dirToWall.offset + moveForw.offset))) Seq(moveAway, moveForw)
          else if (mine.countToPaint(forw, bot) > 0) Seq(moveForw)
          else Seq(mine.moveToUnpainted(pos, bot))
        } else Seq(RotateCW, mine.moveToUnpainted(pos, bot))
      }
    } else Seq(mine.moveToUnpainted(pos, bot))
  }

  def followWall(act: Action): Seq[Action] = act match {
/*
    case move: Move =>
//       println(s"checking $move")
// val result = {
      val left = move.rotateCCW
      val right = move.rotateCW
      val dusting = bot.arms(2) == left.offset
      if (dusting) {
        // (if (bot.arms.length == 3) Seq(RotateCW) else Seq.empty[Action]) ++
        {val reach = pos +: bot.arms.drop(2).map(mine.toOffset(_) + pos).takeWhile(mine.canMoveTo)
        val next = reach.last + mine.toOffset(left.offset)
        if (mine.unpainted(next)) Seq(left, move)
        else if (mine.canMoveTo(pos + mine.toOffset(right.offset))) {
          val folded = reach.length < bot.arms.length - 2
          val reach2 = (pos + mine.toOffset(move.offset)) +: bot.arms.drop(2).map(x => mine.toOffset(x + move.offset) + pos).takeWhile(mine.canMoveTo)
          val folded2 = reach2.length < bot.arms.length - 1
          val reach3 = (pos + mine.toOffset(move.offset + move.offset)) +: bot.arms.drop(2).map(x => mine.toOffset(x + move.offset + move.offset) + pos).takeWhile(mine.canMoveTo)
          val folded3 = reach3.length < bot.arms.length - 1
          if (folded && folded2 && folded3 && mine.unpainted(reach2.last)) Seq(right, move)
          else Seq(move)
        } else Seq(move)}
      } else if (bot.arms(2) == move.offset) {
        // (if (bot.arms.length > 3) Seq(RotateCCW) else Seq.empty[Action]) ++
        (if (mine.canMoveTo(pos + mine.toOffset(left.offset)) && mine.unpainted(pos + mine.toOffset(left.offset + left.offset + move.offset))) Seq(left, move)
        else if (mine.canMoveTo(pos + mine.toOffset(right.offset)) &&
                 !mine.canMoveTo(pos + mine.toOffset(left.offset + move.offset + move.offset)) &&
                 !mine.unpainted(pos + mine.toOffset(left.offset + move.offset + move.offset + move.offset))) Seq(right, move)
        else Seq(move))
      } else if (bot.arms(2) != right.offset) {
        // (if (bot.arms.length > 3) Seq(RotateCW) else Seq.empty[Action]) ++
        (if (mine.canMoveTo(pos + mine.toOffset(left.offset)) && mine.unpainted(pos + mine.toOffset(left.offset + left.offset - move.offset))) Seq(left, move)
        else if (mine.canMoveTo(pos + mine.toOffset(right.offset)) &&
                 !mine.canMoveTo(pos + mine.toOffset(left.offset)) &&
                 !mine.unpainted(pos + mine.toOffset(left.offset + move.offset))) Seq(right, move)
        else Seq(move))
      } else Seq(move)
// }; println(s"  yielding $result"); result
*/
    case _ => Seq(act)
  }

  def checkWall(from: Int, offset: Int) = {
    mine.canMoveTo((1 to 1000).view.map(from + _ * offset).dropWhile(mine.painted).head)
  }

  def dist(where: Point) = (mine.toPoint(pos) - where).magnitude

  def installTeleporter: Option[Path] = {
    val candidates = toList.takeWhile(_.bot.inventory.getOrElse('R', 0) > 0).reverse
    val tardy = candidates.maxBy(_.timeSinceChange)
    val droppers = candidates.takeWhile(_ ne tardy)
    val where = mine.toPoint(tardy.pos)
    val here = droppers.minBy(_.dist(where))
    val savings = tardy.timeSinceChange - here.dist(where) - 1
    if (savings > 0) {
      println(s"Planting at ${mine.toPoint(here.pos)} to save $savings while moving to $where")
      Some(here(Reset))
    } else None
  }
}

sealed trait Action
sealed trait Move extends Action {
  def offset: Point
  def rotateCW: Move
  def rotateCCW: Move
}
object MoveUp                        extends Move   { override def toString = "W"; def offset = Point( 0,  1); def rotateCW = MoveRight; def rotateCCW = MoveLeft  }
object MoveDown                      extends Move   { override def toString = "S"; def offset = Point( 0, -1); def rotateCW = MoveLeft;  def rotateCCW = MoveRight }
object MoveRight                     extends Move   { override def toString = "D"; def offset = Point( 1,  0); def rotateCW = MoveDown;  def rotateCCW = MoveUp    }
object MoveLeft                      extends Move   { override def toString = "A"; def offset = Point(-1,  0); def rotateCW = MoveUp;    def rotateCCW = MoveDown  }
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

case class Mine(startX: Int, startY: Int, width: Int, height: Int, cells: IndexedSeq[Char], portCost: Array[(Int, Shift)], numBoosters: Int) {
  override def toString = {
    val o = s"(${startX + width},${startY + height})"
    s"""${" "*(width - o.length max 0)}$o
${cells.grouped(width).map(_.mkString).toVector.reverse.mkString("\n")}
($startX,$startY) - (${startX + width},${startY + height})"""
  }

  def toPos(p: Point) = (p.y - startY) * width + p.x - startX
  def toOffset(p: Point) = (p.y) * width + p.x
  def toPoint(p: Int) = Point(p % width + startX, p / width + startY)

  def reachable(where: Int, bot: Bot): Seq[Int] = {
    val fixed = where +: bot.arms.take(2).map(toOffset(_) + where).filter(canMoveTo)
    val springing = bot.arms.drop(2).map(toOffset(_) + where).takeWhile(canMoveTo)
    fixed ++ springing
  }
  def paint(where: Point, bot: Bot): Mine = paint(toPos(where), bot)
  def paint(where: Int, bot: Bot): Mine = {
    val reach = reachable(where, bot)
    if (reach.exists(unpainted))
      copy(cells = reach.foldLeft(cells)((c, o) =>
        c.updated(o, c(o) match {
          case '.' => '*'
          case x => x.toLower
        })
      ))
    else this
  }
  def countToPaint(where: Int, bot: Bot): Int = {
    if (canMoveTo(where)) reachable(where, bot).count(unpainted) else 0
  }

  def finished = !cells.exists(c => c == '.' || c.isUpper)

  def apply(pos: Int) = if (pos < 0 || pos >= cells.length) '!' else cells(pos)
  def painted(pos: Int) = {
    val d = apply(pos)
    d == '*' || d.isLower
  }
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

  def updated(pos: Int, c: Char) = copy(cells = cells.updated(pos, c), numBoosters = if (c == '*' && cells(pos).isLetter) numBoosters - 1 else numBoosters)

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

  def moveToPos(pos: Int, target: Int, dir: Move): Option[Action] = {
    val order = Seq(dir.rotateCCW, dir, dir.rotateCW, dir.rotateCW.rotateCW)

    val cost = scala.collection.mutable.Map[Int, Int]().withDefaultValue(Int.MaxValue)
    val open = scala.collection.mutable.Queue[Int]()
    cost(pos) = 0;
    for { (d, k) <- order.zipWithIndex } {
      val p = pos + toOffset(d.offset)
      if (canMoveTo(p)) {
        cost(p) = 4 + k
        open.enqueue(p)
      }
    }
    while (open.nonEmpty) {
      val p = open.dequeue()
      for (o <- Seq(-width, -1, 1, width)) {
        val p2 = p + o
        if (canMoveTo(p2)) {
          if (p2 == target) {
            return Some(order(cost(p) & 3))
          }
          if (cost(p2) > cost(p) + 4) {
            cost(p2) = cost(p) + 4
            open.enqueue(p2)
          }
        }
      }
    }
    return None
  }

  def moveToBooster(pos: Int): Option[Move] = {
    if (numBoosters == 0) return None
    Seq(MoveLeft, MoveUp, MoveRight, MoveDown).find { m =>
      val p2 = pos + toOffset(m.offset)
      grabbable(p2)
    } match {
      case Some(m) => return Some(m)
      case _ =>
    }

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
      if (cost(p) > 20) return None
      for (o <- Seq(-width, -1, 1, width)) {
        val p2 = p + o
        if (canMoveTo(p2)) {
          if (grabbable(p2)) {
            return Some(Seq(MoveLeft, MoveUp, MoveRight, MoveDown)(cost(p) & 3))
          }
          if (cost(p2) > cost(p) + 4) {
            cost(p2) = cost(p) + 4
            open.enqueue(p2)
          }
        }
      }
    }
    return None
  }

  def moveToUnpainted(pos: Int, bot: Bot): Action = {
    Seq(MoveLeft, MoveUp, MoveRight, MoveDown).find { m =>
      val p2 = pos + toOffset(m.offset)
      unpainted(p2) || countToPaint(p2, bot) > 0
    } match {
      case Some(m) => return m
      case _ =>
    }

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
        if (canMoveTo(p2)) {
          if (countToPaint(p2, bot) > 0) {
            return Seq(MoveLeft, MoveUp, MoveRight, MoveDown)(cost(p) & 3)
          }
          if (cost(p2) > cost(p) + 4) {
            cost(p2) = cost(p) + 4
            open.enqueue(p2)
          }
        }
      }
    }
    return Wait
  }

  def plant(pos: Int): Mine = {
    val where = toPoint(pos)
    val shift = Shift(where.x, where.y)
    val cost = portCost.clone()
    cost(pos) = (0, shift)
    var progressing = true
    while (progressing) {
      progressing = false
      for {
        p1 <- (0 until cells.length) ++ (cells.length - 1 to 0 by -1)
        if canMoveTo(p1)
        o <- Seq(-width, -1, 1, width)
        p2 = p1 + o
        if p2 >= 0 && p2 < cells.length
        if cost(p1)._1 - 1 > cost(p2)._1
      } {
        cost(p1) = (cost(p2)._1 + 1, shift)
        progressing = true
      }
    }
    copy(portCost = cost).updated(pos, 't')
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
    Mine(minX, minY, width, height, withBoosters, Array.fill(withBoosters.length)((Int.MaxValue, Shift(0,0))), problem.boosters.count(_.code != 'X'))
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

object Point {
  implicit def fromMove(m: Move): Point = m.offset
}
case class Point(x: Int, y: Int) {
  override def toString = s"($x,$y)"
  def rotateCW = Point(y, -x)
  def rotateCCW = Point(-y, x)
  def + (that: Point) = Point(this.x + that.x, this.y + that.y)
  def - (that: Point) = Point(this.x - that.x, this.y - that.y)
  def * (n: Int) = Point(x * n, y * n)
  def magnitude = x.abs + y.abs
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

