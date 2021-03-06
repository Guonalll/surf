package surf.abm.agents.abbf.activities

import surf.abm.agents.abbf.activities.ActivityTypes.WORKING
import surf.abm.agents.{Agent, UrbanAgent}
import surf.abm.agents.abbf.{ABBFAgent, Place, TimeProfile}
import surf.abm.main.SurfABM


/**
  * An activity (of type [[surf.abm.agents.abbf.activities.ActivityTypes.WORKING]]) that causes the agent to
  * go to their work place.
  */
case class WorkActivity(
                    override val timeProfile: TimeProfile,
                    override val agent: ABBFAgent,
                    override val place: Place)
  extends FixedActivity (
    activityType = WORKING,
    timeProfile = timeProfile,
    agent = agent,
    place = Place(agent.home, WORKING))
    with Serializable
{

  // These variables define the different things that the agent could be doing in order to satisfy the work activity
  // (Note: in SleepActivity, these are defined as case classes that extend a sealed trait, but this way is probably
  // more efficient)

  private val WORKING = 1
  private val TRAVELLING = 2
  private val INITIALISING = 3
  private var currentAction = INITIALISING

  /**
    * This makes the agent actually perform the activity. They can either be at work or travelling there
    *
    * @return True if at work, false otherwise
    */
  override def performActivity(): Boolean = {

    this.currentAction match {

      case INITIALISING => {
        Agent.LOG.debug(agent, "is initialising WorkActivity")
        // See if the agent is at work
        if (this.place.location.equalLocation(this.agent.location())) {
          Agent.LOG.debug(agent, "is at work. Starting to work.")
          currentAction = WORKING // Next iteration the agent will start to work
        }
        else {
          Agent.LOG.debug( agent, "iis not at work. Travelling there.")
          this.agent.newDestination(Option(this.place.location))
          currentAction = TRAVELLING
        }

      }

      case TRAVELLING => {
        if (this.agent.atDestination()) {
          Agent.LOG.debug(agent, "has reached their workplace. Starting to work")
          currentAction = WORKING
        }
        else {
          Agent.LOG.debug(agent, "is travelling to work.")
          agent.moveAlongPath()
        }
      }

      case WORKING => {
        Agent.LOG.debug(agent, "is working")
        return true
      }

    }
    // Only get here if the agent isn't working, so must return false.
    return false
  }

  override def activityChanged(): Unit = {
    this.currentAction = INITIALISING
    this._currentIntensityDecrease = 0d
  }

  /**
    * The amount that work activity should increase at each iteration
    * @return
    */
  override def backgroundIncrease(): Double = {
    return 1d / ((2d/3d) * SurfABM.ticksPerDay)
  }

  /**
    * The amount that work activity will go down by if an agent is working.
    * @return
    */
  override def reduceActivityAmount(): Double = {
    return 3d / SurfABM.ticksPerDay
  }

  override val MINIMUM_INTENSITY_DECREASE = 0.45

}
