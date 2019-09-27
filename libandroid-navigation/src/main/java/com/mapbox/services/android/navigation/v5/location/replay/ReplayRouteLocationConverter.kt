package com.mapbox.services.android.navigation.v5.location.replay

import android.location.Location
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import java.util.*

internal class ReplayRouteLocationConverter(route: DirectionsRoute, private var speed: Int, private var delay: Int) {
    private val ONE_SECOND_IN_MILLISECONDS = 1000
    private val ONE_KM_IN_METERS = 1000.0
    private val ONE_HOUR_IN_SECONDS = 3600
    private val REPLAY_ROUTE = "com.mapbox.services.android.navigation.v5.location.replay" + ".ReplayRouteLocationEngine"

    private var route: DirectionsRoute? = null
    private val distance: Double
    private var currentLeg: Int = 0
    private var currentStep: Int = 0
    private var time: Long = 0

    val isMultiLegRoute: Boolean
        get() =
            route?.legs()?.let { list ->
                list.count() > 1
            } ?: false


    init {
        initialize()
        update(route)
        this.distance = calculateDistancePerSec()
    }

    fun updateSpeed(customSpeedInKmPerHour: Int) {
        this.speed = customSpeedInKmPerHour
    }

    fun updateDelay(customDelayInSeconds: Int) {
        this.delay = customDelayInSeconds
    }

    fun toLocations(): List<Location> {
        val stepPoints = calculateStepPoints()

        return calculateMockLocations(stepPoints)
    }

    fun initializeTime() {
        time = System.currentTimeMillis()
    }

    /**
     * Interpolates the route into even points along the route and adds these to the points list.
     *
     * @param lineString our route geometry.
     * @return list of sliced [Point]s.
     */
    fun sliceRoute(lineString: LineString): List<Point> {
        val distanceMeters = TurfMeasurement.length(lineString, TurfConstants.UNIT_METERS)
        if (distanceMeters <= 0) {
            return emptyList()
        }

        val points = ArrayList<Point>()
        var i = 0.0
        while (i < distanceMeters) {
            val point = TurfMeasurement.along(lineString, i, TurfConstants.UNIT_METERS)
            points.add(point)
            i += distance
        }
        return points
    }

    fun calculateMockLocations(points: List<Point>): List<Location> {
        val pointsToCopy = ArrayList(points)
        val mockedLocations = ArrayList<Location>()
        for (point in points) {
            val mockedLocation = createMockLocationFrom(point)

            if (pointsToCopy.size >= 2) {
                val bearing = TurfMeasurement.bearing(point, pointsToCopy[1])
                mockedLocation.bearing = bearing.toFloat()
            }
            time += (delay * ONE_SECOND_IN_MILLISECONDS).toLong()
            mockedLocations.add(mockedLocation)
            pointsToCopy.remove(point)
        }

        return mockedLocations
    }

    private fun update(route: DirectionsRoute) {
        this.route = route
    }

    /**
     * Converts the speed value to m/s and delay to seconds. Then the distance is calculated and returned.
     *
     * @return a double value representing the distance given a speed and time.
     */
    private fun calculateDistancePerSec(): Double {
        return speed.toDouble() * ONE_KM_IN_METERS * delay.toDouble() / ONE_HOUR_IN_SECONDS
    }

    private fun initialize() {
        this.currentLeg = 0
        this.currentStep = 0
    }

    private fun calculateStepPoints(): List<Point> {
        val stepPoints = ArrayList<Point>()

        val line = LineString.fromPolyline(
                route!!.legs()!![currentLeg].steps()!![currentStep].geometry()!!, Constants.PRECISION_6)
        stepPoints.addAll(sliceRoute(line))
        increaseIndex()

        return stepPoints
    }

    private fun increaseIndex() {
        if (currentStep < route!!.legs()!![currentLeg].steps()!!.size - 1) {
            currentStep++
        } else if (currentLeg < route!!.legs()!!.size - 1) {
            currentLeg++
            currentStep = 0
        }
    }

    private fun createMockLocationFrom(point: Point): Location {
        val mockedLocation = Location(REPLAY_ROUTE)
        mockedLocation.latitude = point.latitude()
        mockedLocation.longitude = point.longitude()
        val speedInMetersPerSec = (speed * ONE_KM_IN_METERS / ONE_HOUR_IN_SECONDS).toFloat()
        mockedLocation.speed = speedInMetersPerSec
        mockedLocation.accuracy = 3f
        mockedLocation.time = time
        return mockedLocation
    }
}
