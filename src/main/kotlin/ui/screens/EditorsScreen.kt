package org.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.ui.components.*
import org.example.ui.viewmodel.EditorsViewModel

/**
 * Écran Éditeur - Page principale de gestion des éditeurs.
 *
 * Architecture ViewModel :
 * - Gestion d'état centralisée dans EditorsViewModel
 * - Séparation UI / logique métier
 */
@Composable
fun EditorsScreen(
    onBack: () -> Unit
) {
    // Initialisation du ViewModel
    val viewModel = remember { EditorsViewModel() }

    // Récupération des états
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val selectedEditorId by viewModel.selectedEditorId

    // Nettoyage à la sortie
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onCleared()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Bouton retour + Titre
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Éditeurs",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            EditorTable(
                modifier = Modifier.fillMaxWidth(),
                publishers = viewModel.publishers,
                onEditorSelected = { editorId: String? ->
                    viewModel.selectEditor(editorId)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                EditorDetailPanel(
                    modifier = Modifier.weight(1f),
                    selectedEditorId = selectedEditorId
                )

                IncidentsComparisonChart(
                    modifier = Modifier.weight(1f)
                )
            }

            PerformanceIndicators(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

