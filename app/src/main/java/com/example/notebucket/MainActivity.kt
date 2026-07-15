package com.example.notebucket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notebucket.ui.nav.NoteBucketNavGraph
import com.example.notebucket.ui.theme.NoteBucketTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteBucketTheme {
                NoteBucketNavGraph()
            }
        }
    }
}
