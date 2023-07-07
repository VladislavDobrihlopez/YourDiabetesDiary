package com.example.yourdiabetesdiary.presentation.screens.composition

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.yourdiabetesdiary.models.DiaryEntry
import com.example.yourdiabetesdiary.models.Mood
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState

@OptIn(
    ExperimentalPagerApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun CompositionContent(
    pagerState: PagerState,
    paddingValues: PaddingValues,
    title: String,
    description: String,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onSaveDiaryButtonClicked: (DiaryEntry) -> Unit
) {
    val verticalScrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            )
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .verticalScroll(verticalScrollState)
                .weight(1f)
        ) {
            val bunchOfMoods = rememberSaveable {
                val moods = Mood.values()
                //moods.shuffle()
                mutableStateOf(moods)
            }

            HorizontalPager(state = pagerState, count = Mood.values().size) { page ->
                bunchOfMoods.value[page].also { mood ->
                    AsyncImage(
                        modifier = Modifier.size(120.dp),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(mood.icon)
                            .crossfade(true)
                            .build(),
                        contentDescription = mood.name
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = onTitleChanged,
                placeholder = { Text(text = "Title") },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Unspecified,
                    disabledIndicatorColor = Color.Unspecified,
                    unfocusedIndicatorColor = Color.Unspecified,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {

                }),
                maxLines = 1,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                modifier = Modifier.fillMaxSize(),
                value = description,
                onValueChange = onDescriptionChanged,
                placeholder = { Text(text = "Description") },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Unspecified,
                    disabledIndicatorColor = Color.Unspecified,
                    unfocusedIndicatorColor = Color.Unspecified,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onNext = {

                }),
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onClick = {
                    if (title.isNotEmpty() && description.isNotEmpty()) {
                        onSaveDiaryButtonClicked(DiaryEntry().apply {
                            this.title = title.trim()
                            this.description = description.trim()
                        })
                    } else {
                        Toast.makeText(
                            context,
                            "Either title or description shouldn't be empty",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                shape = Shapes().small
            ) {
                Text(text = "Save")
            }
        }
    }
}