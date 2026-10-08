package ca.ilianokokoro.umihi.music.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ca.ilianokokoro.umihi.music.R

data class CountryOption(
    val code: String,
    val name: String,
    val flag: String
)

val AVAILABLE_COUNTRIES = listOf(
    CountryOption("SYSTEM", "Mặc định hệ thống / System Default", "🌐"),
    CountryOption("VN", "Việt Nam (Vietnam)", "🇻🇳"),
    CountryOption("US", "Hoa Kỳ (United States)", "🇺🇸"),
    CountryOption("JP", "Nhật Bản (Japan)", "🇯🇵"),
    CountryOption("KR", "Hàn Quốc (South Korea)", "🇰🇷"),
    CountryOption("GB", "Vương quốc Anh (United Kingdom)", "🇬🇧"),
    CountryOption("FR", "Pháp (France)", "🇫🇷"),
    CountryOption("DE", "Đức (Germany)", "🇩🇪"),
    CountryOption("TH", "Thái Lan (Thailand)", "🇹🇭"),
    CountryOption("ID", "Indonesia", "🇮🇩"),
    CountryOption("MY", "Malaysia", "🇲🇾"),
    CountryOption("SG", "Singapore", "🇸🇬"),
    CountryOption("PH", "Philippines", "🇵🇭"),
    CountryOption("TW", "Đài Loan (Taiwan)", "🇹🇼"),
    CountryOption("HK", "Hồng Kông (Hong Kong)", "🇭🇰"),
    CountryOption("IN", "Ấn Độ (India)", "🇮🇳"),
    CountryOption("AU", "Úc (Australia)", "🇦🇺"),
    CountryOption("CA", "Canada", "🇨🇦"),
    CountryOption("BR", "Brazil", "🇧🇷"),
    CountryOption("MX", "Mexico", "🇲🇽"),
    CountryOption("ES", "Tây Ban Nha (Spain)", "🇪🇸"),
    CountryOption("IT", "Ý (Italy)", "🇮🇹"),
    CountryOption("NL", "Hà Lan (Netherlands)", "🇳🇱"),
    CountryOption("SE", "Thụy Điển (Sweden)", "🇸🇪"),
    CountryOption("NO", "Na Uy (Norway)", "🇳🇴"),
    CountryOption("DK", "Đan Mạch (Denmark)", "🇩🇰"),
    CountryOption("FI", "Phần Lan (Finland)", "🇫🇮"),
    CountryOption("PL", "Ba Lan (Poland)", "🇵🇱"),
    CountryOption("CH", "Thụy Sĩ (Switzerland)", "🇨🇭"),
    CountryOption("AT", "Áo (Austria)", "🇦🇹"),
    CountryOption("BE", "Bỉ (Belgium)", "🇧🇪"),
    CountryOption("PT", "Bồ Đào Nha (Portugal)", "🇵🇹"),
    CountryOption("RU", "Nga (Russia)", "🇷🇺"),
    CountryOption("UA", "Ukraine", "🇺🇦"),
    CountryOption("TR", "Thổ Nhĩ Kỳ (Turkey)", "🇹🇷"),
    CountryOption("SA", "Ả Rập Xê Út (Saudi Arabia)", "🇸🇦"),
    CountryOption("AE", "UAE (United Arab Emirates)", "🇦🇪"),
    CountryOption("EG", "Ai Cập (Egypt)", "🇪🇬"),
    CountryOption("ZA", "Nam Phi (South Africa)", "🇿🇦"),
    CountryOption("NG", "Nigeria", "🇳🇬"),
    CountryOption("AR", "Argentina", "🇦🇷"),
    CountryOption("CL", "Chile", "🇨🇱"),
    CountryOption("CO", "Colombia", "🇨🇴"),
    CountryOption("PE", "Peru", "🇵🇪"),
    CountryOption("NZ", "New Zealand", "🇳🇿"),
    CountryOption("IE", "Ireland", "🇮🇪"),
    CountryOption("CZ", "Cộng hòa Séc (Czechia)", "🇨🇿"),
    CountryOption("HU", "Hungary", "🇭🇺"),
    CountryOption("RO", "Romania", "🇷🇴"),
    CountryOption("GR", "Hy Lạp (Greece)", "🇬🇷"),
    CountryOption("IL", "Israel", "🇮🇱"),
    CountryOption("PK", "Pakistan", "🇵🇰"),
    CountryOption("BD", "Bangladesh", "🇧🇩"),
    CountryOption("KH", "Campuchia (Cambodia)", "🇰🇭"),
    CountryOption("LA", "Lào (Laos)", "🇱🇦"),
    CountryOption("MM", "Myanmar", "🇲🇲"),
    CountryOption("CL", "Chile", "🇨🇱")
).distinctBy { it.code }

@Composable
fun CountrySelectDialog(
    selectedCountryCode: String,
    onSelect: (newCode: String) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            AVAILABLE_COUNTRIES
        } else {
            val query = searchQuery.trim().lowercase()
            AVAILABLE_COUNTRIES.filter {
                it.name.lowercase().contains(query) || it.code.lowercase().contains(query)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(text = stringResource(R.string.select_country_region))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(filteredCountries, key = { it.code }) { country ->
                        val isSelected = country.code.equals(selectedCountryCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(shape = RoundedCornerShape(12.dp))
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onSelect(country.code)
                                        onClose()
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Text(
                                text = "${country.flag}  ${country.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onClose,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.close))
            }
        },
        properties = DialogProperties(dismissOnClickOutside = true)
    )
}
