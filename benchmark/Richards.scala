// Copyright 2006-2008 the V8 project authors. All rights reserved.
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Google Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// Ported by the Dart team to Dart.
// Ported by Jonas Fonseca to Scala.js.

// This is a Scala implementation of the Richards benchmark from:
//
//    http://www.cl.cam.ac.uk/~mr10/Bench.html
//
// The benchmark was originally implemented in BCPL by
// Martin Richards.

package misc

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/**
 * My machine
 * ----------
 * sbt clean 'set scalaVersion := "2.11.7"' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'
 * [info] Richards.run  avgt   20  0.112 ± 0.001  ms/op
 *
 * sbt clean 'set scalaVersion := "2.11.7"' 'set scalacOptions in ThisBuild ++= Seq("-Ybackend:GenBCode")' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'
 * [info] Richards.run  avgt   20  0.127 ± 0.001  ms/op
 *
 * sbt clean 'set scalaVersion := "2.11.7"' 'set scalacOptions in ThisBuild ++= Seq("-optimise")' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'
 * [info] Richards.run  avgt   20  0.115 ± 0.001  ms/op
 *
 * sbt clean 'set scalaVersion := "2.12.0-newopt"' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'
 * [info] Richards.run  avgt   20  0.127 ± 0.001  ms/op
 *
 * sbt clean 'set scalaVersion := "2.12.0-newopt"' 'set scalacOptions in ThisBuild ++= Seq("-Yopt:l:classpath")' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'
 * [info] Richards.run  avgt   20  0.114 ± 0.001  ms/op
 *
 *
 * Linux machine
 * -------------
 * [info] Richards.run  avgt   20  0.426 ± 0.008  ms/op
 * [info] Richards.run  avgt   20  0.429 ± 0.009  ms/op
 * [info] Richards.run  avgt   20  0.427 ± 0.009  ms/op
 * [info] Richards.run  avgt   20  0.423 ± 0.006  ms/op
 * [info] Richards.run  avgt   20  0.428 ± 0.009  ms/op
 *
 */
@BenchmarkMode(Array(Mode.Throughput))
class Richards {
  import Richards._

  @Benchmark
  def run(): Unit = {
    val scheduler = new Scheduler()
    scheduler.addIdleTask(ID_IDLE, 0, null, COUNT)

    var queue = new Packet(null, ID_WORKER, KIND_WORK)
    queue = new Packet(queue, ID_WORKER, KIND_WORK)
    scheduler.addWorkerTask(ID_WORKER, 1000, queue)

    queue = new Packet(null, ID_DEVICE_A, KIND_DEVICE)
    queue = new Packet(queue, ID_DEVICE_A, KIND_DEVICE)
    queue = new Packet(queue, ID_DEVICE_A, KIND_DEVICE)
    scheduler.addHandlerTask(ID_HANDLER_A, 2000, queue)

    queue = new Packet(null, ID_DEVICE_B, KIND_DEVICE)
    queue = new Packet(queue, ID_DEVICE_B, KIND_DEVICE)
    queue = new Packet(queue, ID_DEVICE_B, KIND_DEVICE)
    scheduler.addHandlerTask(ID_HANDLER_B, 3000, queue)

    scheduler.addDeviceTask(ID_DEVICE_A, 4000, null)

    scheduler.addDeviceTask(ID_DEVICE_B, 5000, null)

    scheduler.schedule()
  }
}

object Richards {
  final val DATA_SIZE = 4
  final val COUNT = 2000

  final val ID_IDLE = 0
  final val ID_WORKER = 1
  final val ID_HANDLER_A = 2
  final val ID_HANDLER_B = 3
  final val ID_DEVICE_A = 4
  final val ID_DEVICE_B = 5
  final val NUMBER_OF_IDS = 6

  final val KIND_DEVICE = 0
  final val KIND_WORK = 1
}

/**
 * A scheduler can be used to schedule a set of tasks based on their relative
 * priorities.  Scheduling is done by maintaining a list of task control blocks
 * which holds tasks and the data queue they are processing.
 */
class Scheduler {

  var queueCount = 0
  var holdCount = 0
  var currentTcb: TaskControlBlock = null
  var currentId: Int = 0
  var list: TaskControlBlock = null
  val blocks = new Array[TaskControlBlock](Richards.NUMBER_OF_IDS)

  /// Add an idle task to this scheduler.
  def addIdleTask(id: Int, priority: Int, queue: Packet, count: Int) {
    addRunningTask(id, priority, queue, new IdleTask(this, 1, count))
  }

  /// Add a work task to this scheduler.
  def addWorkerTask(id: Int, priority: Int, queue: Packet) {
    addTask(id, priority, queue, new WorkerTask(this, Richards.ID_HANDLER_A, 0))
  }

  /// Add a handler task to this scheduler.
  def addHandlerTask(id: Int, priority: Int, queue: Packet) {
    addTask(id, priority, queue, new HandlerTask(this))
  }

  /// Add a handler task to this scheduler.
  def addDeviceTask(id: Int, priority: Int, queue: Packet) {
    addTask(id, priority, queue, new DeviceTask(this))
  }

  /// Add the specified task and mark it as running.
  def addRunningTask(id: Int, priority: Int, queue: Packet, task: Task) {
    addTask(id, priority, queue, task)
    currentTcb.setRunning()
  }

  /// Add the specified task to this scheduler.
  def addTask(id: Int, priority: Int, queue: Packet, task: Task) {
    currentTcb = new TaskControlBlock(list, id, priority, queue, task)
    list = currentTcb
    blocks(id) = currentTcb
  }

  /// Execute the tasks managed by this scheduler.
  def schedule() {
    currentTcb = list
    while (currentTcb != null) {
      if (currentTcb.isHeldOrSuspended()) {
        currentTcb = currentTcb.link
      } else {
        currentId = currentTcb.id
        currentTcb = currentTcb.run()
      }
    }
  }

  /// Release a task that is currently blocked and return the next block to run.
  def release(id: Int): TaskControlBlock = {
    val tcb = blocks(id)
    if (tcb == null) tcb
    else {
      tcb.markAsNotHeld()
      if (tcb.priority > currentTcb.priority) tcb
      else currentTcb
    }
  }

  /**
   * Block the currently executing task and return the next task control block
   * to run.  The blocked task will not be made runnable until it is explicitly
   * released, even if new work is added to it.
   */
  def holdCurrent(): TaskControlBlock = {
    holdCount += 1
    currentTcb.markAsHeld()
    currentTcb.link
  }

  /**
   * Suspend the currently executing task and return the next task
   * control block to run.
   * If new work is added to the suspended task it will be made runnable.
   */
  def suspendCurrent(): TaskControlBlock = {
    currentTcb.markAsSuspended()
    currentTcb
  }

  /**
   * Add the specified packet to the end of the worklist used by the task
   * associated with the packet and make the task runnable if it is currently
   * suspended.
   */
  def queue(packet: Packet): TaskControlBlock = {
    val t = blocks(packet.id)
    if (t == null) t
    else {
      queueCount += 1
      packet.link = null
      packet.id = currentId
      t.checkPriorityAdd(currentTcb, packet)
    }
  }
}

object TaskState {
  /// The task is running and is currently scheduled.
  final val RUNNING = 0

  /// The task has packets left to process.
  final val RUNNABLE = 1

  /**
   * The task is not currently running. The task is not blocked as such and may
   * be started by the scheduler.
   */
  final val SUSPENDED = 2

  /// The task is blocked and cannot be run until it is explicitly released.
  final val HELD = 4

  final val SUSPENDED_RUNNABLE = SUSPENDED | RUNNABLE
  final val NOT_HELD = ~HELD
}

/**
 * A task control block manages a task and the queue of work packages associated
 * with it.
 *
 * @param id        The id of this block.
 * @param priority  The priority of this block.
 * @param queue     The queue of packages to be processed by the task.
 */
class TaskControlBlock(val link: TaskControlBlock, val id: Int, val priority: Int, var queue: Packet, task: Task) {

  var state = if (queue == null) TaskState.SUSPENDED else TaskState.SUSPENDED_RUNNABLE

  def setRunning() {
    state = TaskState.RUNNING
  }

  def markAsNotHeld() {
    state = state & TaskState.NOT_HELD
  }

  def markAsHeld() {
    state = state | TaskState.HELD
  }

  def isHeldOrSuspended(): Boolean = {
    (state & TaskState.HELD) != 0 ||
      (state == TaskState.SUSPENDED)
  }

  def markAsSuspended() {
    state = state | TaskState.SUSPENDED
  }

  def markAsRunnable() {
    state = state | TaskState.RUNNABLE
  }

  /// Runs this task, if it is ready to be run, and returns the next task to run.
  def run(): TaskControlBlock = {
    val packet = if (state == TaskState.SUSPENDED_RUNNABLE) queue else null
    if (packet != null) {
      queue = packet.link
      state = if (queue == null) TaskState.RUNNING else TaskState.RUNNABLE
    }
    task.run(packet)
  }

  /**
   * Adds a packet to the worklist of this block's task, marks this as
   * runnable if necessary, and returns the next runnable object to run
   * (the one with the highest priority).
   */
  def checkPriorityAdd(task: TaskControlBlock, packet: Packet): TaskControlBlock = {
    if (queue == null) {
      queue = packet
      markAsRunnable()
      if (priority > task.priority) this
      else task
    } else {
      queue = packet.addTo(queue)
      task
    }
  }

  override def toString = s"tcb { ${task}@${state} }"
}

/**
 *  Abstract task that manipulates work packets.
 *
 * @param scheduler	  The scheduler that manages this task.
 */
sealed abstract class Task(scheduler: Scheduler) {
  def run(packet: Packet): TaskControlBlock
}

/**
 * An idle task doesn't do any work itself but cycles control between the two
 * device tasks.
 *
 * @param v1	  A seed value that controls how the device tasks are scheduled.
 * @param count	The number of times this task should be scheduled.
 */
class IdleTask(scheduler: Scheduler, var v1: Int, var count: Int) extends Task(scheduler) {

  def run(packet: Packet): TaskControlBlock = {
    count -= 1
    if (count == 0) {
      scheduler.holdCurrent()
    } else if ((v1 & 1) == 0) {
      v1 = v1 >> 1
      scheduler.release(Richards.ID_DEVICE_A)
    } else {
      v1 = (v1 >> 1) ^ 0xD008
      scheduler.release(Richards.ID_DEVICE_B)
    }
  }

}

/**
 * A task that suspends itself after each time it has been run to simulate
 * waiting for data from an external device.
 */
class DeviceTask(scheduler: Scheduler) extends Task(scheduler) {

  var v1: Packet = null

  def run(packet: Packet): TaskControlBlock = {
    if (packet == null) {
      if (v1 == null)
        scheduler.suspendCurrent()
      else {
        val v = v1
        v1 = null
        scheduler.queue(v)
      }
    } else {
      v1 = packet
      scheduler.holdCurrent()
    }
  }

}

/**
 * A task that manipulates work packets.
 *
 * @param v1	A seed used to specify how work packets are manipulated.
 * @param v2	Another seed used to specify how work packets are manipulated.
 */
class WorkerTask(scheduler: Scheduler, var v1: Int, var v2: Int) extends Task(scheduler) {

  def run(packet: Packet): TaskControlBlock = {
    if (packet == null) {
      scheduler.suspendCurrent()
    } else {
      if (v1 == Richards.ID_HANDLER_A) {
        v1 = Richards.ID_HANDLER_B
      } else {
        v1 = Richards.ID_HANDLER_A
      }
      packet.id = v1
      packet.a1 = 0
      // replacing this range-foreach with a while loop makes an 1.8x improvement for scala.js,
      // but no improvement on the JVM - it's the only range-foreach in the program, so there's
      // no megamorphism.
//      var i = 0
//      while (i < Richards.DATA_SIZE) {
      for (i <- 0 until Richards.DATA_SIZE) {
        v2 += 1
        if (v2 > 26) v2 = 1
        packet.a2(i) = v2
//        i += 1
      }
      scheduler.queue(packet)
    }
  }

}

/**
 * A task that manipulates work packets and then suspends itself.
 */
class HandlerTask(scheduler: Scheduler) extends Task(scheduler) {

  var v1: Packet = null
  var v2: Packet = null

  def run(packet: Packet): TaskControlBlock = {
    if (packet != null) {
      if (packet.kind == Richards.KIND_WORK) {
        v1 = packet.addTo(v1)
      } else {
        v2 = packet.addTo(v2)
      }
    }
    if (v1 != null) {
      val count = v1.a1

      if (count < Richards.DATA_SIZE) {
        if (v2 != null) {
          val v = v2
          v2 = v2.link
          v.a1 = v1.a2(count)
          v1.a1 = count + 1
          return scheduler.queue(v)
        }
      } else {
        val v = v1
        v1 = v1.link
        return scheduler.queue(v)
      }
    }

    scheduler.suspendCurrent()
  }

}

/**
 * A simple package of data that is manipulated by the tasks.  The exact layout
 * of the payload data carried by a packet is not importaint, and neither is the
 * nature of the work performed on packets by the tasks.
 * Besides carrying data, packets form linked lists and are hence used both as
 * data and worklists.
 *
 * @param link	The tail of the linked list of packets.
 * @param id	An ID for this packet.
 * @param kind	The type of this packet.
 */
class Packet(var link: Packet, var id: Int, val kind: Int) {

  var a1 = 0
  val a2 = new Array[Int](Richards.DATA_SIZE)

  /// Add this packet to the end of a worklist, and return the worklist.
  def addTo(queue: Packet): Packet = {
    link = null
    if (queue == null) {
      this
    } else {
      var next = queue
      while (next.link != null)
        next = next.link
      next.link = this
      queue
    }
  }

}
