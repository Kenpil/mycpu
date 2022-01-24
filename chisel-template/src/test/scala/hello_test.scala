package tutorialHello

import chisel3._
import org.scalatest._
import chiseltest._

class HelloTest extends FlatSpec with ChiselScalatestTester {
  "Hello World ! test" should "use numbers" in{
    test(new Hello) { c =>
      while (!c.io.exit.peek().litToBoolean){
        c.clock.step(1)
      }
    }
  }
}