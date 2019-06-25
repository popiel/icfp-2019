# Judging Feedback

Well, this was a very good contest problem, with very few clarifications needed.
Unfortunately, there were two significant flaws in presentation:

1. The contest was unusually time-zone specific this year.
   Having an update within 10 hours of the end of the lightning division
   ensured that people who would normally sleep during that period
   were at a severe disadvantage.

   Having lambda-chain mining commence four hours after the update that announced it
   and then proceeding in 15 to 30 minute increments that could never be revisited
   ensured that people who happened to be sleeping (or otherwise taking a break)
   were again at a severe disadvantage.
   Rescheduling the start of mining until 14 hours after announcement was a reasonable mitigation.

   Requiring submission of sources along with post-contest writeups
   within two hours of of the end of the contest
   (with the deadline announced only at the start of those two hours)
   ensured that anyone who was sleeping
   (or incoherent from staying up for final code tweaks)
   missed the opportunity to be considered for prizes.
   Please note that this 2-hour time window was 3am to 5am
   for people on the west coast of North America.

2. The contest was biased against people who did not have a modern python installation
   in an unsecured location.
   The lambda-chain mining data collection and block submission
   was described only with respect to a provided client written in python.
   Anyone with an older OS (e.g. Ubuntu 16.04, still in its support window)
   had to backport either a python 3.6 installation or the client code.
   Anyone with security concerns about running an untrusted codebase
   had to do a code audit before participating in the severely time-limited mining.

   While information about the protocol
   for accessing the block chain information and submitting solutions
   was available by asking the judges and then mentioned in the F.A.Q.,
   that information was never posted as either an update to the specification
   nor mentioned in any of the tweets.
