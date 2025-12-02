package com.example.cpu

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.cpu.utils.PrefsManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import kotlin.jvm.java


class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val database = FirebaseDatabase.getInstance()
    private val prefsManager = PrefsManager(context)

    fun addGeofence(
        latitude: Double,
        longitude: Double,
        radius: Float,
        id: String
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val geofencePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
                Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        try {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun removeGeofence(id: String) {
        geofencingClient.removeGeofences(listOf(id))
    }
}