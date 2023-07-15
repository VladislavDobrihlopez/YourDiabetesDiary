package com.example.yourdiabetesdiary.presentation.screens.composition

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourdiabetesdiary.data.database.ImageInQueryForUploadingDao
import com.example.yourdiabetesdiary.data.database.models.ImagesForUploadingDbModel
import com.example.yourdiabetesdiary.data.repository.MongoDbDbRepositoryImpl
import com.example.yourdiabetesdiary.domain.RequestState
import com.example.yourdiabetesdiary.models.DiaryEntry
import com.example.yourdiabetesdiary.models.GalleryItem
import com.example.yourdiabetesdiary.models.Mood
import com.example.yourdiabetesdiary.navigation.Screen
import com.example.yourdiabetesdiary.presentation.components.custom_states.GalleryState
import com.example.yourdiabetesdiary.util.retrieveImagesFromFirebaseStorage
import com.example.yourdiabetesdiary.util.toInstant
import com.example.yourdiabetesdiary.util.toRealmInstant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class CompositionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val locallyCachingDao: ImageInQueryForUploadingDao
) : ViewModel() {
    private val _uiState = mutableStateOf(CompositionScreenState())
    val uiState: State<CompositionScreenState>
        get() = _uiState

    private val _galleryState = mutableStateOf(GalleryState())
    val galleryState: State<GalleryState> = _galleryState

    init {
        putRoutedDiaryIdArgument()
        getDiaryInfo()
    }

    private fun putRoutedDiaryIdArgument() {
        _uiState.value =
            CompositionScreenState(selectedDiaryEntryId = savedStateHandle.get<String>(Screen.DiaryEntry.DIARY_ID_ARGUMENT_KEY))
    }

    private fun isInEditMode() = _uiState.value.selectedDiaryEntryId != null

    private fun getDiaryInfo() {
        viewModelScope.launch {
            if (isInEditMode()) {
                val id = _uiState.value.selectedDiaryEntryId
                MongoDbDbRepositoryImpl.pullDiary(org.mongodb.kbson.ObjectId(id!!))
                    .catch { ex ->
                        emit(RequestState.Error(IllegalStateException("Diary already deleted")))
                    }
                    .collect { requestResult ->
                        if (requestResult is RequestState.Success) {
                            Log.d("TEST_DIARY", "mood: ${requestResult.data.mood}")

                            with(requestResult.data) {
                                supervisorScope {
                                    launch {
                                        setNewDate(date = date.toInstant())
                                        setNewTitle(title = title)
                                        setNewDescription(description = description)
                                        setNewMood(mood = mood)
                                    }

                                    launch(Dispatchers.IO) {
                                        retrieveImagesFromFirebaseStorage(imagesUrls = images.toList(),
                                            onCompletedDownloadingItem = { url ->
                                                _galleryState.value =
                                                    GalleryState.setupImagesBasedOnPrevious(
                                                        _galleryState.value
                                                    ).apply {
                                                        addImage(
                                                            GalleryItem(
                                                                localUri = url,
                                                                remotePath = createRemotePath(url.toString())
                                                            )
                                                        )
                                                    }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun createRemotePath(firebaseUrl: String): String {
        val chunks = firebaseUrl.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${FirebaseAuth.getInstance().currentUser?.uid}/$imageName"
    }

    // forms the remote path for the photo in the following way: images/{user_id}/{photo_name}-{photo_id}.{extension}
    fun addImage(uri: Uri, imageType: String) {
        val remotePath =
            "images/${FirebaseAuth.getInstance().currentUser?.uid}/${uri.lastPathSegment}-${System.currentTimeMillis()}.${imageType}"
        Log.d("ADD_IMAGE", "addImage: $remotePath")
        _galleryState.value =
            GalleryState.setupImagesBasedOnPrevious(galleryState.value).apply {
                addImage(GalleryItem(remotePath = remotePath, localUri = uri))
            }
    }

    fun uploadImages() {
        with(FirebaseStorage.getInstance().reference) {
            galleryState.value.images.forEach { image ->
                Log.d("TEST_STORING", "a new to upload: $image")
                val meta = child(image.remotePath)
                meta.putFile(image.localUri)
                    .addOnFailureListener { ex ->
                        Log.d("TEST_STORING", "loading error: $ex")
                    }
                    .addOnSuccessListener {
                        Log.d("TEST_STORING", "success loading")
                    }
                    .addOnProgressListener { session ->
                        viewModelScope.launch(Dispatchers.IO) {
                            val sessionUri = session.uploadSessionUri
                            if (sessionUri != null) {
                                locallyCachingDao.addImage(
                                    model = ImagesForUploadingDbModel(
                                        localUri = image.localUri.toString(),
                                        remotePath = image.remotePath,
                                        sessionUri = sessionUri.toString()
                                    )
                                )
                            }
                        }
                    }
            }
        }
    }

    fun queueImageForDeletion(galleryItem: GalleryItem) {
        _galleryState.value = GalleryState.setupImagesBasedOnPrevious(_galleryState.value).apply {
            removeInTwoStorages(galleryItem)
        }
        Log.d("TEST_STORING", "queueImageForDeletion: ${galleryItem.toString()}")
        Log.d("TEST_STORING", "queueImageForDeletion: ${_galleryState.value}")
    }

    fun storeDiary(diary: DiaryEntry, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            MongoDbDbRepositoryImpl.upsertEntry(diary.apply {
                _uiState.value.date?.let { updatedOrScreenOpeningTime ->
                    this.date = updatedOrScreenOpeningTime.toRealmInstant()
                }
            }).collect { result ->
                Log.d("TEST_STORING", "$result")
                when (result) {
                    is RequestState.Success -> {
                        deleteImagesRelatedToDiary(galleryState.value.imagesForDeletion.map {it.remotePath})
                        uploadImages()
                        Log.d("TEST_STORING", "storeDiary: ${galleryState.toString()}")
                        withContext(Dispatchers.Main) {
                            onSuccess()
                        }
                    }

                    is RequestState.Error -> withContext(Dispatchers.Main) {
                        onFailure(result.ex.message.toString())
                    }

                    else -> onFailure("Unexpected problem occurred")
                }
            }
        }
    }

    fun deleteDiary(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            MongoDbDbRepositoryImpl.deleteDiary(diaryId = ObjectId(uiState.value.selectedDiaryEntryId!!))
                .collect { result ->
                    Log.d("TEST_DELETING", "$result")
                    when (result) {
                        is RequestState.Success -> {
                            uiState.value.selectedDiaryEntryId?.let {
                                deleteImagesRelatedToDiary(remoteUris = galleryState.value.images.map { it.remotePath })
                            }
                            withContext(Dispatchers.Main) {
                                onSuccess()
                            }
                        }

                        is RequestState.Error ->
                            withContext(Dispatchers.Main) {
                                onFailure(result.ex.message.toString())
                            }

                        else -> onFailure("Unexpected problem occurred")
                    }
                }
        }
    }

    private fun deleteImagesRelatedToDiary(remoteUris: List<String>) {
        Log.d("TEST_STORING", "remote: $remoteUris")
        val storageReference = FirebaseStorage.getInstance().reference
        remoteUris.forEach { path ->
            storageReference.child(path).delete()
        }
    }

    fun setNewTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun setNewDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun setNewMood(mood: String) {
        _uiState.value = _uiState.value.copy(mood = Mood.valueOf(mood))
    }

    fun setNewDate(date: Instant) {
        _uiState.value = _uiState.value.copy(date = date)
    }

    fun setNewDateAndTime(zonedDateTime: ZonedDateTime) {
        _uiState.value = _uiState.value.copy(date = zonedDateTime.toInstant())
    }
}