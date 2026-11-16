package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberCut
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldRender
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportHubScreen(
    onPickHtmlFile: () -> Unit,
    onLoadSampleStory: () -> Unit,
    statusBanner: String? = null,
    onDismissBanner: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBg)
    ) {
        // Atmospheric Studio Grid & Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2838BDF8), Color.Transparent),
                    center = Offset(size.width * 0.25f, size.height * 0.18f),
                    radius = size.width * 0.75f
                ),
                center = Offset(size.width * 0.25f, size.height * 0.18f),
                radius = size.width * 0.75f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x20F59E0B), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.72f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.85f, size.height * 0.72f),
                radius = size.width * 0.65f
            )

            val step = 54.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x0DFFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0x0DFFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Brand Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(listOf(CyanPulse, Color(0xFF0284C7)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = "FrameCraft Logo",
                            tint = Color(0xFF04131E),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "FRAMECRAFT STUDIO",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanPulse
                        )
                        Text(
                            text = "HTML Motion Video Studio",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                }

                // Hero Headline
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "আপলোড করা কোড থেকে সরাসরি ভিডিও তৈরি ও এডিট করুন",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "HTML মোশন গ্রাফিক্স ফাইল লোড করে প্রিভিউ দেখুন, টাইমলাইনে এলিমেন্ট এডিট বা ডিলিট করুন, গ্যালারি থেকে ফটো/ভিডিও/অডিও যোগ করুন এবং 240p থেকে 1080p পর্যন্ত MP4 এক্সপোর্ট করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                if (statusBanner != null) {
                    Surface(
                        color = Color(0xFF3B1219),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanPulse,
                                modifier = Modifier
                                    .clickable { onDismissBanner() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Primary File Upload Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.5.dp, CyanPulse.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onPickHtmlFile() }
                        .testTag("import_html_card")
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(CyanPulse.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = "Upload HTML File",
                                tint = CyanPulse,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "HTML ফাইল ইনপুট করুন (.html / .htm)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Canvas বা CSS অ্যানিমেশন কোড সম্পূর্ণ লোড হয়ে ভিডিও আকারে প্রিভিউ হবে",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = onPickHtmlFile,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = Color(0xFF04131E)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("import_html_button")
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ফাইল সিলেক্ট করুন (Choose HTML)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Load Bundled Sample Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoadSampleStory() }
                            .padding(16.dp)
                            .testTag("load_sample_story_card"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmberCut.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Load Sample",
                                tint = AmberCut,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "স্যাম্পল স্টোরি লোড করুন (72s • 9:16)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "News Documentary (৭টি দৃশ্য) — সরাসরি প্রিভিউ ও এক্সপোর্ট টেস্ট করুন",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Sample",
                            tint = AmberCut
                        )
                    }
                }

                // Capabilities Grid
                Text(
                    text = "স্টুডিও ফিচারসমূহ",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2
                ) {
                    FeatureTile(
                        icon = Icons.Filled.TouchApp,
                        accent = CyanPulse,
                        title = "হাত দিয়ে এডিট ও ডিলিট",
                        desc = "স্ক্রিনে ট্যাপ করে এলিমেন্ট সরান, টেক্সট বদলান বা ডিলিট দিন",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureTile(
                        icon = Icons.Filled.AutoAwesome,
                        accent = AmberCut,
                        title = "টাইমলাইন কন্ট্রোল",
                        desc = "দৃশ্য বা এলিমেন্ট টাইমলাইনে আগে-পিছে নিন ও ট্রিম করুন",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureTile(
                        icon = Icons.Filled.AddCircleOutline,
                        accent = VioletMotion,
                        title = "গ্যালারি মিডিয়া",
                        desc = "ফটো, ভিডিও ক্লিপ, অডিও ও মোশন গ্রাফিক্স যোগ করুন",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureTile(
                        icon = Icons.Filled.HighQuality,
                        accent = EmeraldRender,
                        title = "ডাইরেক্ট MP4 এক্সপোর্ট",
                        desc = "240p, 360p, 480p, 720p, 1080p রেজোলিউশনে সরাসরি সেভ",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Direct format pills
                Surface(
                    color = StudioSurfaceCard.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "কোনো পরিবর্তন ছাড়াই সরাসরি ভিডিও ফরম্যাটে এক্সপোর্ট ও গ্যালারিতে সেভ করা যাবে।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FeatureTile(
    icon: ImageVector,
    accent: Color,
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
