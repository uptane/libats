package com.advancedtelematic.libats.test

import java.time.Instant

import org.scalatest.matchers.{BeMatcher, MatchResult}

trait InstantMatchers {
  def after(other: Instant): BeMatcher[Instant] = (me: Instant) => MatchResult(me.isAfter(other),
    me.toString + " was not after " + other,
    me.toString + " was after " + other)

  def before(other: Instant): BeMatcher[Instant] = (me: Instant) => MatchResult(me.isBefore(other),
    me.toString + " was not before " + other,
    me.toString + " was before " + other)
}

object InstantMatchers extends InstantMatchers