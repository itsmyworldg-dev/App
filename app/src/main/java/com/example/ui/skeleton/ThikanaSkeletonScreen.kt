package com.example.ui.skeleton

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsNone
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.ThikanaLine
import com.example.ui.theme.ThikanaPaper
import com.example.ui.theme.ThikanaPink
import com.example.ui.theme.ThikanaViolet

/**
 * Creates a shimmering linear gradient brush with warm Thikana tones for skeleton placeholders.
 */
@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1400f,
    colors: List<Color> = listOf(
        Color(0xFFEDE8E3),
        Color(0xFFFBF8F5),
        Color(0xFFFFEEF3),
        Color(0xFFEDE8E3)
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
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
 * Pixel-perfect opening skeleton screen that exactly mirrors the user's Vibes / Feed UI:
 * 1. Top bar: Glowing dot + "Mera Thikaana" gradient title + (Music, Notification, Friends) icons.
 * 2. Segmented Pill Tab Bar: "Explore" (active purple pill) vs "Following".
 * 3. Feed Post Cards:
 *    - Story-ring avatar + "raiprashant" + "Follow" pill + Red pin location ("Bhusur Treeway").
 *    - Shimmering hero photo (aspect ratio ~0.95) with top story progress line and "1/2" badge.
 *    - Action icons: Heart, Comment, Paper-plane share, Bookmark.
 *    - "2 likes" and full caption.
 * 4. Second post card peeking from the bottom.
 * 5. Floating Voice Nav FAB with equalizer soundwave bars.
 * 6. Floating Glassmorphism Bottom Dock: DISCOVER, VIBES (active pink), elevated '+' button, MESSAGES, PROFILE.
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
                .statusBarsPadding()
        ) {
            // 1. Top Header Bar
            SkeletonHeaderBar()

            // 2. Segmented Pill Tab Row: Explore | Following
            SkeletonSegmentedTabRow()

            // 3. Scrollable Feed Posts
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Post 1 (Main card in screenshot)
                SkeletonFeedCard(
                    username = "raiprashant",
                    location = "Bhusur Treeway",
                    distance = "12429.2 km away",
                    likes = "2 likes",
                    caption = "Just added Bhusur Treeway to the map!",
                    imageCountBadge = "1/2",
                    shimmerBrush = shimmerBrush
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Post 2 (Peeking at bottom of screenshot)
                SkeletonFeedCard(
                    username = "raiprashant",
                    location = "Getalsud Dam - View Point",
                    distance = "12433.3 km away",
                    likes = "5 likes",
                    caption = "Sunday road trip with friends to Getalsud Dam!",
                    imageCountBadge = null,
                    shimmerBrush = shimmerBrush,
                    isSecondPost = true
                )

                // Bottom clearance for floating Voice FAB and Bottom Dock
                Spacer(modifier = Modifier.height(110.dp + navBarBottomInset))
            }
        }

        // 4. Floating Voice Nav FAB (soundwave icon above dock on right)
        FloatingVoiceFab(
            navBarBottomInset = navBarBottomInset,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        // 5. Floating Glassmorphism Bottom Dock
        SkeletonBottomDock(
            navBarBottomInset = navBarBottomInset,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Top Header Bar matching exact screenshot:
 * Glowing dot + "Mera Thikaana" gradient title + 3 action buttons (Music note, Bell, Friends).
 */
@Composable
private fun SkeletonHeaderBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Glowing dot + "Mera Thikaana" gradient brand text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Glowing gradient brand dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF3B5C), Color(0xFFFFB020))
                        )
                    )
            )

            // "Mera Thikaana" with signature warm gradient
            Text(
                text = "Mera Thikaana",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFFFF5252),
                            Color(0xFFFF4081),
                            Color(0xFF7C4DFF),
                            Color(0xFF536DFE)
                        )
                    ),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            )
        }

        // Right: 3 header action buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Music note
            Icon(
                painter = painterResource(id = R.drawable.ic_top_music),
                contentDescription = "Music",
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(20.dp)
            )

            // 2. Notification bell
            Icon(
                imageVector = Icons.Default.NotificationsNone,
                contentDescription = "Notifications",
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(21.dp)
            )

            // 3. Friends / Community
            Icon(
                painter = painterResource(id = R.drawable.ic_top_friends),
                contentDescription = "Friends",
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/**
 * Segmented Pill Tab Bar: "Explore" (active purple pill) vs "Following" (muted).
 */
@Composable
private fun SkeletonSegmentedTabRow(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFFF3EDF5))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Explore" active pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF6C2BD9), Color(0xFF4338CA))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Explore",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }

            // "Following" inactive tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Following",
                    color = Color(0xFF8E8E93),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

/**
 * Feed Post Card matching exact post in user's UI.
 */
@Composable
private fun SkeletonFeedCard(
    username: String,
    location: String,
    distance: String,
    likes: String,
    caption: String,
    imageCountBadge: String?,
    shimmerBrush: Brush,
    isSecondPost: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // User Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Avatar with Instagram Story Gradient border
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, InstagramStoryGradient, CircleShape)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                }

                // Name & Location
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = username,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )

                        // "Follow" Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF9E86C8), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Follow",
                                color = Color(0xFF7C5CBF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Red Location Pin + Place Name + Distance
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFFF3B5C),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = location,
                            color = Color(0xFFFF3B5C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = distance,
                            color = Color(0xFF8E8E93),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Media Container with Shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isSecondPost) 1.15f else 0.96f)
                .clip(RoundedCornerShape(20.dp))
                .background(shimmerBrush)
        ) {
            // Story Progress Segment Line & "1/2" Multi-photo Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented story bar line
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                )

                if (imageCountBadge != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = imageCountBadge,
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Icons Row: Heart, Comment, Send / Bookmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Heart / Like
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = Color(0xFF262626),
                    modifier = Modifier.size(23.dp)
                )

                // Comment
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Comment",
                    tint = Color(0xFF262626),
                    modifier = Modifier.size(21.dp)
                )

                // Send / Paper Plane
                Icon(
                    painter = painterResource(id = R.drawable.ic_feed_send),
                    contentDescription = "Share",
                    tint = Color(0xFF262626),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bookmark
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = "Save",
                tint = Color(0xFF262626),
                modifier = Modifier.size(23.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Likes Count
        Text(
            text = likes,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E)
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Caption: username bold + caption text
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E))) {
                    append(username)
                    append(" ")
                }
                withStyle(style = SpanStyle(color = Color(0xFF1E1E1E))) {
                    append(caption)
                }
            },
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

/**
 * Floating Voice Nav FAB in bottom right above dock with pink/purple gradient and equalizer soundwave bars.
 */
@Composable
private fun FloatingVoiceFab(
    navBarBottomInset: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 16.dp, bottom = 78.dp + navBarBottomInset)
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF3B80), Color(0xFFAA47BC))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // White soundwave equalizer bars matching screenshot icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            val barHeights = listOf(7.dp, 16.dp, 12.dp, 8.dp)
            barHeights.forEach { height ->
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(height)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

/**
 * Floating Glassmorphism Bottom Dock:
 * DISCOVER, VIBES (Active), Central '+' elevated button, MESSAGES, PROFILE.
 */
@Composable
private fun SkeletonBottomDock(
    navBarBottomInset: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp + navBarBottomInset)
            .shadow(10.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, ThikanaLine)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 1. Discover
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_discover),
                    contentDescription = "Discover",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "DISCOVER",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93)
                )
            }

            // 2. Vibes (Active Tab!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_vibes),
                    contentDescription = "Vibes",
                    tint = ThikanaPink,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "VIBES",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ThikanaPink
                )
            }

            // 3. Central Create '+' Button elevated above dock
            Box(
                modifier = Modifier
                    .offset(y = (-14).dp)
                    .size(46.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF2A85), Color(0xFF8A2BE2))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 4. Messages
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_messages),
                    contentDescription = "Messages",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "MESSAGES",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93)
                )
            }

            // 5. Profile
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_profile),
                    contentDescription = "Profile",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "PROFILE",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93)
                )
            }
        }
    }
}
