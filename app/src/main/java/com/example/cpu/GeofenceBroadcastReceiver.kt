package com.example.cpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        // Check for errors
        if (geofencingEvent?.hasError() ?: true) {
            val errorMessage = "Geofence Error with code: ${geofencingEvent?.errorCode}"
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type (Enter, Exit, etc.)
        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            // Get the geofences that were triggered
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            triggeringGeofences?.forEach { geofence ->
                handleGeofenceTransition(geofence, geofenceTransition)
            } ?: Log.e(TAG, "No geofences triggered")
        } else {
            // Log the unsupported transition type
            Log.w(TAG, "Unsupported geofence transition type: $geofenceTransition")
        }
    }

    private fun handleGeofenceTransition(geofence: Geofence, transitionType: Int) {
        val geofenceId = geofence.requestId

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.i(TAG, "User ENTERED geofence: $geofenceId")
                // TODO: Add your logic for entering the geofence
                // For example, send a notification or update Firebase
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.i(TAG, "User EXITED geofence: $geofenceId")
                // TODO: Add your logic for exiting the geofence
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}
