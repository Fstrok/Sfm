package com.sfm.app.ui.term

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import com.sfm.app.data.Term

// STUB: implemented by the browse agent.
@Composable
fun TermScreen(term: Term) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(term.name) }
