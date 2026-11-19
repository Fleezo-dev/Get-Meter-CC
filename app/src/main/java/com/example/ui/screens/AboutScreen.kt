package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.service.FloatingSpeedOverlayManager
import com.example.service.LocationTrackingService
import com.example.ui.components.CreatorSignatureBanner
import com.example.ui.theme.LocalGetTaxiColors

private const val HDS_PREFS_NAME = "hds_company_prefs"
private const val KEY_HDS_CONTACT_PHONE = "hds_contact_phone"
private const val GET_TAXI_TELEGRAM_GROUP_URL = "https://t.me/+dOFMD6gQOftlYTA1"

/**
 * Habeeb Digital Solutions (HDS) — A unit of Get Taxi.
 * Opened from the GET TAXI yellow header icon or the About Us menu item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalGetTaxiColors.current
    val prefs = remember { context.getSharedPreferences(HDS_PREFS_NAME, Context.MODE_PRIVATE) }
    var hdsContactNumber by remember {
        mutableStateOf(prefs.getString(KEY_HDS_CONTACT_PHONE, "")?.trim().orEmpty())
    }
    var showContactConfigDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!LocationTrackingService.isTripActivelyRunningForBubble()) {
            FloatingSpeedOverlayManager.forceHide(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Habeeb Digital Solutions (HDS)",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("about_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HERO BRAND CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(28.dp))
                    .testTag("about_hero_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color(0xFFFFC107), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.get_taxi_logo),
                            contentDescription = "GET TAXI Logo",
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Habeeb Digital Solutions (HDS)",
                        color = Color(0xFF0F172A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "A unit of Get Taxi",
                        color = Color(0xFFB45309),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Digital Transformation • Custom Software • Automation",
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // HDS COMPANY OVERVIEW CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(24.dp))
                    .testTag("about_business_identity_card")
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF14171C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = "Business",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "COMPANY PROFILE",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Get Taxi • Habeeb Digital Solutions (HDS)",
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "No. 286, Diwan Bahadur Road,\nR.S. Puram, Coimbatore,\nTamil Nadu, India.",
                        color = Color(0xFF1E293B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Habeeb Digital Solutions (HDS), a unit of Get Taxi, builds websites, native Android apps, WhatsApp automation, AI chatbots, billing & transport management platforms, and local lead-generation systems.",
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            // COMMUNITY & BUSINESS CONTACT CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(24.dp))
                    .testTag("about_address_card")
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "GET TAXI COMMUNITY & HDS CONTACT",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )

                    // CLICKABLE GET TAXI TELEGRAM GROUP LINK
                    Surface(
                        color = Color(0xFFF0F9FF),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GET_TAXI_TELEGRAM_GROUP_URL))
                                    )
                                }
                            }
                            .testTag("about_telegram_link")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "GET TAXI Telegram Group",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "GET TAXI Telegram Group",
                                        color = Color(0xFF0369A1),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = GET_TAXI_TELEGRAM_GROUP_URL,
                                        color = Color(0xFF0F172A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open Telegram Group",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // CONFIGURABLE HDS CONTACT CALL / WHATSAPP (DOES NOT USE GPAY 9043743777)
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("about_hds_contact_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "HDS Business Contact (Call / WhatsApp)",
                                        color = Color(0xFF0F172A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = if (hdsContactNumber.isNotBlank()) hdsContactNumber else "Not configured yet",
                                        color = Color(0xFF475569),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { showContactConfigDialog = true },
                                    modifier = Modifier.testTag("hds_configure_contact_button")
                                ) {
                                    Text(
                                        text = if (hdsContactNumber.isNotBlank()) "Edit" else "Configure",
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (hdsContactNumber.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$hdsContactNumber"))
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("hds_call_button")
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Call HDS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val digits = hdsContactNumber.filter { it.isDigit() }
                                            val waNum = if (digits.length == 10) "91$digits" else digits
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum"))
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF14171C),
                                            contentColor = Color(0xFFFFC107)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("hds_whatsapp_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ALL 12 HDS SERVICES CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(24.dp))
                    .testTag("about_services_card")
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "HDS DIGITAL SOLUTIONS & SERVICES",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val services = listOf(
                        ServiceItem(
                            number = 1,
                            title = "Website Design & Development.",
                            icon = Icons.Default.Language
                        ),
                        ServiceItem(
                            number = 2,
                            title = "Android App Development.",
                            icon = Icons.Default.PhoneAndroid
                        ),
                        ServiceItem(
                            number = 3,
                            title = "Billing & Business Management Software.",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong
                        ),
                        ServiceItem(
                            number = 4,
                            title = "WhatsApp Automation.",
                            icon = Icons.AutoMirrored.Filled.Chat
                        ),
                        ServiceItem(
                            number = 5,
                            title = "AI Chatbots and Business Assistants.",
                            icon = Icons.Default.SmartToy
                        ),
                        ServiceItem(
                            number = 6,
                            title = "Google Ads Creation & Campaign Management.",
                            icon = Icons.Default.Campaign
                        ),
                        ServiceItem(
                            number = 7,
                            title = "SEO Services.",
                            icon = Icons.Default.Search
                        ),
                        ServiceItem(
                            number = 8,
                            title = "Google Business Profile Management.",
                            icon = Icons.Default.Storefront
                        ),
                        ServiceItem(
                            number = 9,
                            title = "Custom Software Development.",
                            icon = Icons.Default.Code
                        ),
                        ServiceItem(
                            number = 10,
                            title = "Business Process Automation.",
                            icon = Icons.Default.AutoAwesome
                        ),
                        ServiceItem(
                            number = 11,
                            title = "Taxi and Transport Management Software.",
                            icon = Icons.Default.LocalTaxi
                        ),
                        ServiceItem(
                            number = 12,
                            title = "Landing Pages and Lead Generation.",
                            icon = Icons.Default.TrendingUp
                        )
                    )

                    services.forEachIndexed { index, service ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF14171C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = service.icon,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "${service.number}. ${service.title}",
                                color = Color(0xFF0F172A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (index < services.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFFF8FAFC),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            CreatorSignatureBanner(
                testTag = "about_creator_credit_banner"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showContactConfigDialog) {
        var phoneDraft by remember { mutableStateOf(hdsContactNumber) }
        Dialog(onDismissRequest = { showContactConfigDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Configure HDS Business Contact",
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    OutlinedTextField(
                        value = phoneDraft,
                        onValueChange = { phoneDraft = it },
                        label = { Text("HDS Phone / WhatsApp Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showContactConfigDialog = false },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val clean = phoneDraft.trim()
                                prefs.edit().putString(KEY_HDS_CONTACT_PHONE, clean).apply()
                                hdsContactNumber = clean
                                showContactConfigDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF14171C),
                                contentColor = Color(0xFFFFC107)
                            ),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private data class ServiceItem(
    val number: Int,
    val title: String,
    val icon: ImageVector
)
