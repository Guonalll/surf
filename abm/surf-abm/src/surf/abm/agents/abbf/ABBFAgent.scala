package surf.abm.agents.abbf

import sim.engine.SimState
import sim.util.geo.{GeomPlanarGraphDirectedEdge}
import surf.abm.agents.abbf.activities.ActivityTypes.SHOPPING
import surf.abm.agents.abbf.activities._
import surf.abm.agents.{Agent, UrbanAgent}
import surf.abm.environment.{Building, GeomPlanarGraphEdgeSurf}
import surf.abm.exceptions.RoutingException
import surf.abm.main.{Clock, SurfABM, SurfGeometry}

/**
  *
  * @param state A pointer to the main model instance (<code>SurfABM</code>)
  * @param home The agent's home (starting location). Usually a <code>Building</code>
  *             where (e.g.) they live, but not necessarily. The agent's initial
  *             location is set to be <code>home</code>
  */
abstract class ABBFAgent(override val state:SurfABM, override val home:SurfGeometry[Building])
  extends UrbanAgent(state, home) {

  /**
    * A set of of the [[surf.abm.agents.abbf.activities.Activity]]s that are driving an agent
    */
  var activities: Set[_ <: Activity] = Set.empty



  /**
    * The current activity that the agent is doing. Can be None.
    */
  private var _currentActivity: Option[_ <: Activity] = None
  def currentActivity() = _currentActivity // public accessor to private member

  /**
    * The previous activity that the agent was doing. Can be none.
    */
  private var _previousActivity: Option[_ <: Activity] = None
  def previousActivity() = _previousActivity // public accessor to private member

  /**
    * Work out which activity is the most intense at the moment.
    *
    * @return
    */
  def highestActivity(): Activity = this.activities.maxBy(a => a.intensity())


  override def step(state: SimState): Unit = {

    // A break point for a particular agent
    //if (this.id()==1 && this.state.schedule.getSteps > 163) {
    //  print("BREAK POINT")
    //}

    // A break point for all agents
    //print("BREAK POINT for AGENT %s".format(this.toString()))

    //println(s"\n******  ${Clock.getTime.toString} ********* \n")
    //this.activities.foreach( {case (a,i) => println(s"$a : $i" )}); println("\n") // print activities

    // Update all activity intensities. The amount that they go up by is unique to each activity. Call the '++' function
    // which, in turn, calls the activity's backgroundIncrease() function, which can be overridden by subclasses.
    for (a <- this.activities){
      if (this.currentActivity().isEmpty || a != this._currentActivity.get) {
        a ++() // Increase this activity
      }
    }

    // Check that the agent has increased the current activity by a sufficient amount before changing *and* it
    // is possible to decrease the amount further (if it is almost zero then don't keep going, even if the agent
    // has only been working on the activity for a short while!)
    /*if (this.currentActivity.isDefined && ! this.currentActivity.get.--(simulate=true) ) {
      // The activity is almost zero and can't be reduced
      Agent.LOG.debug(this, s"Activity (${this.currentActivity.toString}) " +
      s"cannot be reduced further (current intensity: ${this.currentActivity.get.intensity()} ${this.currentActivity.get.currentIntensityDecrease()} < ${ABBFAgent.MINIMUM_INTENSITY_DECREASE})")
    }
    else {*/
      if (! this.currentActivity.isDefined) {
        // There is no activity at the moment.
        Agent.LOG.debug(this, s"Activity (${this.currentActivity.toString}) is undefined.")
      }
      // Either the activity has been worked on sufficiently to decrease intensity by a threshold,
      // or another activity is now more important. So now see if it should change.

      // Perform the action to satisfy the current activity
      //val satisfied = highestActivity.performActivity() // THis is wrong! Don't want to perform the highest activity as it might not be the current one.
      if (this.currentActivity.isDefined) {
        val satisfied = this._currentActivity.get.performActivity()
        if (satisfied) {
          // Decrease the activity. See the activity.reduceActivityAmout() function to see how much it will go down by
          // (the '--' function just calls that)
          this._currentActivity.get.--()
        }
      }

      var msg = "" // Create a single log message to write out (limits nu
      if (this.currentActivity.isDefined && this.currentActivity.get.currentIntensityDecrease() < ABBFAgent.MINIMUM_INTENSITY_DECREASE
            && this.currentActivity().get.intensity() > ABBFAgent.HIGHEST_ACTIVITY_THRESHOLD) {
        // The intensity has not gone down enough and is still high enough to be in control
        Agent.LOG.debug(this, s"Activity (${this.currentActivity.toString}) " +
        s"has not reduced sufficiently yet (current intensity decrease so far: ${this.currentActivity.get.currentIntensityDecrease()} < ${ABBFAgent.MINIMUM_INTENSITY_DECREASE})")
      } else {
        // Now find the most intense one, given the current time.
        val highestActivity: Activity = this.highestActivity()
        msg += s"Highest activity: ${highestActivity.toString()}. "
        //println(s"HIGHEST: $highestActivity : ${highestActivity.intensity()}" )

        // Is the highest activity high enough to take control?
        if (highestActivity.intensity() < ABBFAgent.HIGHEST_ACTIVITY_THRESHOLD) {
          // If not, then make the current activity None
          msg += s"Not high enough (${this.highestActivity().intensity()}, setting to None."
          this._changeActivity(None)
          //Agent.LOG.debug(s"Agent ${this.id.toString()} is not doing any activity")
          return // No point in continuing
        }

        // See if the activity needs to change (taking into account that there might not be a current activity)
        if (highestActivity != this.currentActivity.getOrElse(None)) {
          msg += s"Changing from ${this.currentActivity.getOrElse(None)} to ${Some(highestActivity)}. "
          this._changeActivity(Some(highestActivity))
          //Agent.LOG.debug(s"Agent ${this.id.toString()} has changed current activity to ${this.currentActivity.get.toString}")
        }
      }
      Agent.LOG().debug(this, msg)

    }

  //} // else of { check -- with (simulate=true) }

  /**
    * Perform the necessary things to make this agent change its activity.
    *
    * @param newActivity
    */
  private def _changeActivity(newActivity: Option[Activity]): Unit = {
    // Tell the current activity (if there is one) that it's no longer in control.
    this.currentActivity.foreach(a => a.activityChanged()) // Note: the for loop only iterates if an Activity has been defined (nice!)
    this._previousActivity = this.currentActivity // Remember what the current activity was
    this._currentActivity = newActivity
    //Agent.LOG.debug(this, s"has changed activity from ${this.previousActivity.getOrElse("[None]")} to ${this.currentActivity.getOrElse("[None]")}")
  }



}


object ABBFAgent {

  /**
    * The limit for an activity intensity being large enough to take control of the agent. Below this, the activity
    * is not deemed intense enough.
    */
  private val HIGHEST_ACTIVITY_THRESHOLD = 0.1

  /**
    * The minimum amount that the intensity of an activity must decrease before the agent stops trying to satisfy it.
    * This prevents the agents quickly switching from one activity to another
    */
  private val MINIMUM_INTENSITY_DECREASE = 0.5

  //assert(HIGHEST_ACTIVITY_THRESHOLD > MINIMUM_INTENSITY_DECREASE ) // Otherwise background activity intensities could be reduced below 0
                                                                    // not necessary anymore since activities shouldn't go below 0



}
