package org.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Purchase
import org.example.model.PurchaseStats
import org.example.services.ServiceLocator
import org.example.state.NavigationState
import org.example.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

/**
 * Écran d'historique des achats.
 * Affiche les achats récents et les statistiques.
 */
@Composable
fun PurchasesScreen(
    onBack: () -> Unit,
    navigationState: NavigationState
) {
    val dataService = ServiceLocator.dataService
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var stats by remember { mutableStateOf(PurchaseStats(0, 0.0)) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        purchases = dataService.getRecentPurchases(100)
        stats = dataService.getPurchaseStats()
        isLoading = false
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Historique des Achats",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PurchaseStatCard(
                title = "Total achats",
                value = stats.totalPurchases.toString(),
                modifier = Modifier.weight(1f)
            )
            PurchaseStatCard(
                title = "Revenus totaux",
                value = String.format("%.2f€", stats.totalRevenue),
                modifier = Modifier.weight(1f)
            )
            PurchaseStatCard(
                title = "Panier moyen",
                value = if (stats.totalPurchases > 0) 
                    String.format("%.2f€", stats.totalRevenue / stats.totalPurchases)
                else "0.00€",
                modifier = Modifier.weight(1f)
            )
            PurchaseStatCard(
                title = "Achats DLC",
                value = purchases.count { it.isDlc }.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Liste des achats
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (purchases.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Aucun achat enregistré",
                            style = MaterialTheme.typography.h6
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Les achats apparaîtront ici",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Achats récents (${purchases.size})",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        purchases.forEach { purchase ->
                            PurchaseRow(
                                purchase = purchase,
                                onGameClick = {
                                    navigationState.navigateTo(Screen.GameDetail(purchase.gameId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
private fun PurchaseRow(
    purchase: Purchase,
    onGameClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onGameClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colors.surface,
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge type
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (purchase.isDlc) Color(0xFFFF9800) else MaterialTheme.colors.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (purchase.isDlc) "DLC" else "JEU",
                        style = MaterialTheme.typography.caption,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Infos achat
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = purchase.gameName,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Par ${purchase.playerUsername} - ${purchase.platform}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
                Text(
                    text = dateFormat.format(Date(purchase.timestamp)),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
            
            // Prix
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.2f€", purchase.pricePaid),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                if (purchase.isDlc) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = "DLC",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
        }
    }
}
