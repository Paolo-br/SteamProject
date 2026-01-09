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
import org.example.ui.viewmodel.PatchesViewModel

/**
 * Écran Patchs.
 *
 * Architecture ViewModel :
 * - PatchesViewModel gère l'état et le chargement
 * - Écoute les nouveaux patchs en temps réel (Kafka)
 * - Mise à jour automatique de l'UI
 */
@Composable
fun PatchesScreen(onBack: () -> Unit) {
    // Initialisation du ViewModel
    val viewModel = remember { PatchesViewModel() }

    // Récupération des états
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val patches by remember { derivedStateOf { viewModel.patches } }
    val selectedPatchId by viewModel.selectedPatchId

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
                text = "Correctifs & Patchs",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )

            // Badge de nombre de patchs
            if (patches.isNotEmpty()) {
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    color = MaterialTheme.colors.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${patches.size} patchs",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onPrimary
                    )
                }
            }
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
            PatchSummaryCards(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            PatchTable(
                modifier = Modifier.fillMaxWidth(),
                onPatchSelected = { patchId: String? -> viewModel.selectPatch(patchId) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            PatchDetailPanel(
                modifier = Modifier.fillMaxWidth(),
                selectedPatchId = selectedPatchId
            )

            Spacer(modifier = Modifier.height(24.dp))

            RecentPatchActivity(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

