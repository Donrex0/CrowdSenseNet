package com.example.crowdsensenet.data.remote

// import com.example.crowdsensenet.data.local.MeasurementEntity  // TEMPORARILY DISABLED
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = "measurements"

    // TEMPORARILY DISABLED - MEASUREMENT ENTITY USAGE
    /*
    suspend fun uploadMeasurement(measurement: MeasurementEntity): Result<String> {
        return try {
            val data = hashMapOf(
                "deviceId" to measurement.deviceId,
                "timestamp" to measurement.timestamp,
                "rsrp" to measurement.rsrp,
                "rsrq" to measurement.rsrq,
                "pci" to measurement.pci,
                "cellId" to measurement.cellId,
                "networkTechnology" to measurement.networkTechnology,
                "latitude" to measurement.latitude,
                "longitude" to measurement.longitude,
                "uploadStatus" to "uploaded",
                "createdAt" to FieldValue.serverTimestamp()
            )

            val documentRef = firestore.collection(collection).add(data).await()
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    */

    // TEMPORARILY DISABLED - MEASUREMENT ENTITY USAGE
    /*
    suspend fun uploadBatch(measurements: List<MeasurementEntity>): UploadResult {
        return try {
            var successCount = 0
            val batch = firestore.batch()
            
            measurements.forEach { measurement ->
                val data = hashMapOf(
                    "deviceId" to measurement.deviceId,
                    "timestamp" to measurement.timestamp,
                    "rsrp" to measurement.rsrp,
                    "rsrq" to measurement.rsrq,
                    "pci" to measurement.pci,
                    "cellId" to measurement.cellId,
                    "networkTechnology" to measurement.networkTechnology,
                    "latitude" to measurement.latitude,
                    "longitude" to measurement.longitude,
                    "uploadStatus" to "uploaded",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                
                val docRef = firestore.collection(collection).document()
                batch.set(docRef, data)
                successCount++
            }
            
            batch.commit().await()
            UploadResult.Success(successCount)
        } catch (e: Exception) {
            UploadResult.Failure(e.message ?: "Upload failed")
        }
    }
    */

    suspend fun testConnection(): ConnectionResult {
        return try {
            // Try to read a small amount of data to test connection
            firestore.collection(collection)
                .limit(1)
                .get()
                .await()
            ConnectionResult.Success()
        } catch (e: Exception) {
            ConnectionResult.Failure(e.message ?: "Connection failed")
        }
    }

    suspend fun getMeasurementCount(): Result<Long> {
        return try {
            val count = firestore.collection(collection).get().await().size()
            Result.success(count.toLong())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMeasurement(documentId: String): Result<Boolean> {
        return try {
            firestore.collection(collection).document(documentId).delete().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}