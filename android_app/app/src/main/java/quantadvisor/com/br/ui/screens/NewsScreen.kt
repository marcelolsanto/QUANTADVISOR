package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.Noticia
import quantadvisor.com.br.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBackClick: () -> Unit,
    viewModel: NewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredNews by viewModel.filteredNews.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Intelligence Terminal", color = PrimaryColor, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.carregarNoticias() }) {
                        Icon(Icons.Default.Refresh, null, tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            
            // Filtros de Impacto (Reativo)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Todas", "ALTO", "MÉDIO", "BAIXO").forEach { impacto ->
                    FilterChip(
                        selected = uiState.filter == impacto,
                        onClick = { viewModel.setFilter(impacto) },
                        label = { Text(impacto, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryColor,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceContainer,
                            labelColor = TextMuted
                        ),
                        border = null
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            } else if (filteredNews.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma notícia de impacto ${uiState.filter}", color = TextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredNews, key = { it.id }) { news ->
                        NewsCard(news)
                    }
                }
            }
        }
    }
}

@Composable
fun NewsCard(news: Noticia) {
    val impactColor = when (news.impacto) {
        "ALTO" -> VendaColor
        "MÉDIO" -> AlertaColor
        else -> CompraColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = impactColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, impactColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        "IMPACTO ${news.impacto}", 
                        color = impactColor, 
                        fontSize = 9.sp, 
                        fontWeight = FontWeight.Black, 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(news.hora, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(news.titulo, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            
            Spacer(Modifier.height(8.dp))
            
            Text(news.resumo, color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(news.fonte.uppercase(), color = InfoColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ler mais", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
