# ICFP 2019 contest entry for Invisible Imp

Yup, I'm at it again, participating in the ICFP Programming Contest.
Problem details can be found at https://icfpcontest2019.github.io/.

Enjoy!

# After-Contest Summary

This was a fun contest.
Scoring required only proof-of-work against a published problem set,
with (mostly) no requirement for any particular computing environment.
In broad strokes, this was a path optimization problem over a rasterized map.
The details of the contest were intricate enough
that I wasn't able to fully implement the entire thing.
However, there was no requirement to actually _use_ all of the different features,
and a very respectable showing could be made while ignoring sigificant portions
of the specification.

Unlike many path optimization problems,
this one required complete space-filling as well.
This meant that standard point-a to point-b pathfinders were of limited utility,
and could actually degrade performance
(by leaving small and disconnected unpainted corners which would have to be visited later).

## Maps

The maps were encoded as a set of closed polygons, grid-aligned, with only 90 degree turns.
There were guarantees of space between all walls and obstacles,
so I didn't need to worry about overlaps, either.
Rasterizing this was trivial using the even-odd winding rule for checking if a point is inside a polygon.

The specification for maps indicated that all coordinates would be natural numbers,
without giving an upper bound,
and without requiring the maps to be near the origin.
A question to the judges also did not yield an upper bound.
As such, I coded things so that the raster image for the map was shifted toward the origin
by the minimum of each dimensional coordinate,
thus supporting a reasonable size map that was offset arbitrarily.
However, in practice, all maps were close to the origin... so this flexibility was wasted.

As I typically do,
I flattened the two-dimensional map into a single dimension,
with each direction being a particular offset size within the map.
To maintain the edge of the map (and keep from wrapping around from one side to the other),
I introduced a sentinel map square between each row
(effectively increasing the width of the map by 1).
Overall, this made it trivially easy to make most of the code orientation-agnostic.

## State Management

I kept all state in (effectively) immutable data structures.
Some attention was paid to making separating out portions that would change separately;
e.g. the robot's position was recorded at a higher level than the rest of the robot state,
and separately from the map, too,
so that if the robot was travelling without painting or picking up anything,
a minimal amount of state data would have to be re-allocated.
Combining this with structural sharing,
I could maintain a complete history of all states anywhere along any of the explored paths,
without consuming inordinate amounts of memory.

## Pathfinding

I initially implemented a trivially greedy pathfinding algorithm,
moving toward the nearest unpainted space.
Some directional biases kept the robot moving in generally straight lines.

Over time, I tried to reasonably maintain brush orientation and implement wall-following;
these were surprisingly difficult to do without maintaining state of which wall I was actually following,
which I was initially reluctant to do.
I thus wasted a lot of coding time on ineffective code.

I never came up with a reasonable method for chunking the map,
so all my pathfinding was global.
This increased the cost of pathfinding significantly,
which meant that I never progressed beyond greedy algorithms.
Optimization was quite limited.

## Boosters

There were 5 types of boosters:

- **B - Brush Size**

  The B booster could be used to increase the brush size attached to the robot(s)
  that was used for painting.
  The rules about reachability of the squares for painting
  made the choice of geometry for the brush fairly simple;
  extending it straight out in a direct line from the robot made the computations simplest,
  without significantly degrading overall range.

  I fully implemented using the B boosters.
- **D - Drill**

  The D booster could be used for digging through walls and obstacles to make the path shorter.

  While I have code to properly maintain state if the drill is active,
  I never wrote anything to actually _use_ the drill.
- **F - Fast**

  The F booster would double the movement speed of the robot for a limited time.
  For the purposes of painting, the intermediate square was also visited.
  Using an F booster could theoretically make it hard to enter a narrow passage,
  due to missed alignment;
  however, bumping up against a wall could adjust alignment (sacrificing the second move),
  so this wasn't a practical issue.

  I implemented using F boosters as soon as I acquired them.
  Not optimal, but close enough.
- **R - Reset Points**

  The R boosters could be used to create reset points for teleportation.
  Since you had to visit a square with the booster to set it up,
  this could not be used to reach arbitrary portions of the map.
  However, it could save quite a lot of backtracking.

  I implemented using R boosters as a post-processing step,
  identifying the longest movement without painting,
  and setting up a reset point to bypass it.
  Unfortunately, because of interactions with the F booster
  (and possibly picking up other boosters on the way),
  I had it recalculate the path from the point of placement...
  which made my implementation computationally expensive.
- **C - Cloning**

  Using a C booster while on a spawn point
  (indicated by a fixed X booster on the map)
  would clone the robot,
  which would then use a separate instruction stream for movement.
  Inventory was shared among all robots,
  so a booster picked up by one robot could be used by another.

  Cloning broke some fundamental assumptions in my state management
  and greedy path finding,
  so even though it would potentially create _HUGE_ wins,
  I never implemented it.
