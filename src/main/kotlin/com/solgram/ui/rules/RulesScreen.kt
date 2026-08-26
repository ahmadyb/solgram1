package com.solgram.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.domain.rules.ForwardRule
import com.solgram.domain.rules.RulesEngine
import com.solgram.ui.theme.SolgramTheme

@Composable
fun RulesScreen(
    rules: List<ForwardRule>,
    onAddRule: (ForwardRule) -> Unit,
    onEditRule: (ForwardRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onTestRule: (ForwardRule, String) -> Unit,
    theme: SolgramTheme
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var regexTestInput by remember { mutableStateOf("") }
    var regexPattern by remember { mutableStateOf("") }
    var regexResults by remember { mutableStateOf(emptyList<String>()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Forward Rules", style = MaterialTheme.typography.headlineMedium)
        Text("Name, source chats, destination chats, extraction mode (full/ca/regex with live tester), prefix, min trust, duplicate window, send interval")

        Button(onClick = { showAddDialog = true }) { Text("Add Rule") }

        // Regex live tester
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Regex Live Tester")
                TextField(value = regexPattern, onValueChange = { regexPattern = it; regexResults = RulesEngine.testRegex(it, regexTestInput) }, placeholder = { Text("Regex pattern") })
                TextField(value = regexTestInput, onValueChange = { regexTestInput = it; regexResults = RulesEngine.testRegex(regexPattern, it) }, placeholder = { Text("Test text") })
                Text("Matches: ${regexResults.joinToString()}")
            }
        }

        LazyColumn {
            items(rules) { rule ->
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(rule.name, style = MaterialTheme.typography.titleMedium)
                        Text("Sources: ${rule.sourceChatIds.size}, Dests: ${rule.destinationChatIds.size}, Mode: ${rule.extractionMode}, MinTrust: ${rule.minTrust}")
                        Text("Conditions: ${rule.conditions.size} chain requirements")
                        Row {
                            Button(onClick = { onEditRule(rule) }) { Text("Edit") }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(onClick = { onDeleteRule(rule.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Rule") },
                text = { Text("Rule creation form would be here - name, sources, dests, extraction mode, etc. Including conditional rule chains: RuleCondition with AND/OR logic") },
                confirmButton = { Button(onClick = { showAddDialog = false }) { Text("Add") } },
                dismissButton = { Button(onClick = { showAddDialog = false }) { Text("Cancel") } }
            )
        }
    }
}
