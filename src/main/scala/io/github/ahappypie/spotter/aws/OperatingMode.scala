package io.github.ahappypie.spotter.aws

object OperatingMode extends Enumeration {
  type OperatingMode = Value
  val IMMEDIATE, BACKFILL = Value
}
