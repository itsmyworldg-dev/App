package com.example.ui.skeleton

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ThikanaLine
import com.example.ui.theme.ThikanaPaper
import com.example.ui.theme.ThikanaPaperDim
import com.example.ui.theme.ThikanaPink
import com.example.ui.theme.ThikanaViolet

/**
 * Creates a shimmering linear gradient brush with warm Thikana brand tones.
 */
@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1400f,
    colors: List<Color> = listOf(
        Color(0xFFF1EAE2),
        Color(0xFFFFF7F2),
        Color(0xFFFFECF1),
        Color(0xFFF1EAE2)
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(translateAnimation - 350f, translateAnimation - 350f),
        end = Offset(translateAnimation, translateAnimation)
    )
}

/**
 * High-fidelity Skeleton Loading Screen shown on cold start to eliminate any white screen / flash.
 * Renders Mera Thikaana's signature top header, stories carousel, category pills, feed post cards,
 * and bottom dock placeholders with a polished shimmering pulse.
 */
@Composable
fun ThikanaSkeletonScreen(
    navBarBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ThikanaPaper)
            .testTag("thikana_skeleton_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            // 1. Top Header Bar Skeleton
            SkeletonHeaderBar(shimmerBrush = shimmerBrush)

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Stories Tray Skeleton
            SkeletonStoriesRow(shimmerBrush = shimmerBrush)

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Category Filter Chips Skeleton
            SkeletonCategoryChipsRow(shimmerBrush = shimmerBrush)

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Feed Place Cards Skeleton
            SkeletonFeedCard(shimmerBrush = shimmerBrush, isFirst = true)
            Spacer(modifier = Modifier.height(16.dp))
            SkeletonFeedCard(shimmerBrush = shimmerBrush, isFirst = false)

            // Spacing to clear bottom dock
            Spacer(modifier = Modifier.height(100.dp + navBarBottomInset))
        }

        // 5. Fixed Bottom Navigation Dock Skeleton
        SkeletonBottomDock(
            shimmerBrush = shimmerBrush,
            navBarBottomInset = navBarBottomInset,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SkeletonHeaderBar(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand logo & title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Mera Thikaana Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
            Column {
                Text(
                    text = "Mera Thikaana",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ThikanaViolet
                )
                Box(
                    modifier = Modifier
                        .width(55.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }

        // Header Actions Placeholder
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search capsule
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(shimmerBrush)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFA89F95),
                        modifier = Modifier.size(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }
            }

            // Notification icon circle
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = Color(0xFFA89F95),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SkeletonStoriesRow(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. "Your Thikana" Story Item
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ThikanaPink.copy(alpha = 0.5f), ThikanaViolet.copy(alpha = 0.5f))
                                )
                            )
                            .padding(2.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(shimmerBrush)
                        )
                    }
                    // Plus badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ThikanaPink)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }

        // 2-6. Other stories items
        items(5) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFF5280).copy(alpha = 0.45f),
                                    Color(0xFF8A2BE2).copy(alpha = 0.45f),
                                    Color(0xFFFFB020).copy(alpha = 0.45f)
                                )
                            )
                        )
                        .padding(2.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
private fun SkeletonCategoryChipsRow(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    val chipWidths = listOf(56.dp, 72.dp, 84.dp, 68.dp, 76.dp, 64.dp)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chipWidths.size) { index ->
            if (index == 0) {
                Box(
                    modifier = Modifier
                        .width(chipWidths[index])
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(ThikanaPink.copy(alpha = 0.2f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(chipWidths[index])
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
private fun SkeletonFeedCard(
    shimmerBrush: Brush,
    isFirst: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, ThikanaLine.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // User header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // User Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // User Name bar
                        Box(
                            modifier = Modifier
                                .width(if (isFirst) 120.dp else 95.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                        // Location & Time bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = ThikanaPink.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(if (isFirst) 140.dp else 110.dp)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }

                // Three dots placeholder
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hero Media Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isFirst) 1.25f else 1.35f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shimmerBrush),
                contentAlignment = Alignment.Center
            ) {
                // Subtle Center Placeholder Icon
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp)
                )

                // Category pill overlay at top left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.65f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Icons Row (Like, Comment, Share, Bookmark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Heart
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color(0xFFA89F95),
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(shimmerBrush)
                        )
                    }

                    // Comment
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFFA89F95),
                            modifier = Modifier.size(19.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(shimmerBrush)
                        )
                    }

                    // Share
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color(0xFFA89F95),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bookmark
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = Color(0xFFA89F95),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Caption placeholder lines
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

@Composable
private fun SkeletonBottomDock(
    shimmerBrush: Brush,
    navBarBottomInset: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp),
        color = Color.White.copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(0.75.dp, ThikanaLine)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // 1. Discover / Map
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = ThikanaViolet.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }

                // 2. Vibes / Feed
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }

                // 3. Central Create "+" Button with signature gradient
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ThikanaPink, ThikanaViolet)
                            )
                        )
                        .shadow(6.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 4. Messages
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFFA89F95),
                        modifier = Modifier.size(20.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }

                // 5. Profile
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonOutline,
                        contentDescription = null,
                        tint = Color(0xFFA89F95),
                        modifier = Modifier.size(21.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}
