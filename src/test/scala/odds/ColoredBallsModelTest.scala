/**

 The colored ball example, example 1.1 from

 http://people.csail.mit.edu/milch/papers/blog-chapter.pdf

``An urn contains an unknown number of balls--say, a number chosen from
a Poisson or a uniform distributions. Balls are equally likely to be blue or
green. We draw some balls from the urn, observing the color of each
and replacing it. We cannot tell two identically colored balls apart;
furthermore, observed colors are wrong with probability 0.2.  How many
balls are in the urn? Was the same ball drawn twice?''

http://okmij.org/ftp/kakuritu/blip/colored_balls.ml

**/

package odds

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

trait ColoredBallsModel extends OddsLang {
  def forall[A](obs: IndexedSeq[A], p: A => Rand[Boolean]) = {
    def check(i: Int): Rand[Boolean] =
      if (i==obs.length) always(true)
      else p(obs(i)) && check(i+1)
    check(0)
  }

  sealed abstract class Color
  case object Blue extends Color
  case object Green extends Color

  def opposite_color(c: Color) = c match {
    case Blue => Green
    case Green => Blue
  }

  // The observation of color is faulty, with 20% error rate
  def observed_color(c: Color): Rand[Color] =
    flip(0.8).map(if (_) c else opposite_color(c))

  val nballs_max = 8

  def nballs_prior(): Rand[Int] =
    uniform(1 to nballs_max : _*)
  def ball_colors_prior(): IndexedSeq[Rand[Color]] =
    (0 until nballs_max).map(_ => uniform(Blue, Green))

  def draw(nballs: Rand[Int], ball_colors: IndexedSeq[Rand[Color]]) = {
    for (n <- nballs;
         b <- uniform(0 until n: _*);
         c <- ball_colors(b);
         o <- observed_color(c)) yield (b, c, o)
  }

  def model_nballs(obs: IndexedSeq[Color]) = {
    val nballs = nballs_prior()
    val ball_colors = ball_colors_prior()
    def matches_draw(obs_color: Color) =
      for ((_, _, o) <- draw(nballs, ball_colors)) yield o == obs_color
    nballs when forall(obs, matches_draw)
  }

  // was the same ball drawn twice?
  def model_duplicate(obs: IndexedSeq[Color]) = {
    val nballs = nballs_prior()
    val ball_colors = ball_colors_prior()
    val drawn = obs.foldLeft(always(Map.empty[Int, Int])){(map,obs_color) =>
      for ((b, c, o) <- draw(nballs, ball_colors);
           if o == obs_color;
           m <- map)
      yield m.updated(b, m.getOrElse(b, 0)+1)
    }
    drawn.map(_.values.min)
  }
}

class ColoredBallsModelTest extends FlatSpec with ShouldMatchers {
  behavior of "Colored Balls Model"

  it should "reproduce the experimental results from Milch et al paper" in {
    new ColoredBallsModel with LocalImportanceSampling with OddsPrettyPrint {
      /// TODO(namin): 5000 should be enough according to Oleg
      ///              we need to duplicate the accuracy
      val d = sample(50000, 3, error = 1e-10)(model_nballs((1 to 10).map(_ => Blue)))
      show(normalize(d), "(LIS) Ten balls were drawn, and all appeared blue.")
    }
  }

  it should "find the min number of draws of the same ball" in {
    new ColoredBallsModel with LocalImportanceSampling with OddsPrettyPrint {
      val d = sample(20000, 3, error = 1e-10)(model_duplicate((1 to 10).map(_ => Blue)))
      show(normalize(d), "(LIS) Smallest dup distribution.")
    }
  }
}
