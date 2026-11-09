package com.example.trackpro.screens.vehicleScreens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackpro.R
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.pressableRow
import com.example.trackpro.components.specLabel
import com.example.trackpro.extrasForUI.CustomTextField
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.online.CatalogResult
import com.example.trackpro.online.VehicleCatalog
import com.example.trackpro.online.VehicleCatalogOption
import com.example.trackpro.online.VehicleSpecVariant
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentSoft
import com.example.trackpro.theme.fieldLive
import kotlinx.coroutines.launch

/** Suggestions shown under a make or model field at once; typing narrows them. */
private const val MaxSuggestions = 6

/**
 * Bike makes to suggest while typing. The catalogue has no free list of motorcycle makes, and
 * the search only needs the name, so a typed make outside this list works just the same.
 */
private val BikeMakes = listOf(
    "Aprilia", "Benelli", "BMW", "CFMoto", "Ducati", "Harley-Davidson", "Honda", "Husqvarna",
    "Kawasaki", "KTM", "Moto Guzzi", "MV Agusta", "Royal Enfield", "Suzuki", "Triumph", "Yamaha"
)

/**
 * The top of the car form: find the vehicle in TrackBoard's catalogue and pre-fill the form from
 * its spec sheet. Optional in every way - signed out, offline or with no match, it says so in a
 * line and the form below is filled in by hand as before.
 */
@Composable
fun VehicleLookupCard(
    catalog: VehicleCatalog,
    onPicked: (variant: VehicleSpecVariant, motorcycle: Boolean, year: Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var motorcycle by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<VehicleSpecVariant?>(null) }
    var pickedYear by remember { mutableStateOf<Int?>(null) }

    AppCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(Modifier.animateContentSize()) {
            SectionLabel(stringResource(R.string.lookup_title))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.lookup_hint),
                style = TrackProType.body,
                color = TrackProTheme.colors.textMuted
            )
            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToggleChip(stringResource(R.string.lookup_kind_car), !motorcycle, { motorcycle = false; picked = null })
                ToggleChip(stringResource(R.string.lookup_kind_bike), motorcycle, { motorcycle = true; picked = null })
            }
            Spacer(Modifier.height(Spacing.md))

            val current = picked
            when {
                !catalog.isSignedIn -> Notice(stringResource(R.string.lookup_signed_out))
                current != null -> PickedSummary(current, pickedYear) { picked = null }
                motorcycle -> BikeLookup(catalog) { picked = it; pickedYear = it.yearFrom; onPicked(it, true, it.yearFrom) }
                else -> CarLookup(catalog) { variant, year ->
                    picked = variant; pickedYear = year; onPicked(variant, false, year)
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.lookup_source),
                style = TrackProType.label,
                color = TrackProTheme.colors.textFaint
            )
        }
    }
}

/** Make, then model, then year, then the version. Each step narrows the next. */
@Composable
private fun CarLookup(catalog: VehicleCatalog, onPicked: (VehicleSpecVariant, Int) -> Unit) {
    var makes by remember { mutableStateOf<CatalogResult<List<VehicleCatalogOption>>?>(null) }
    var make by remember { mutableStateOf<VehicleCatalogOption?>(null) }
    var makeQuery by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<CatalogResult<List<VehicleCatalogOption>>?>(null) }
    var model by remember { mutableStateOf<VehicleCatalogOption?>(null) }
    var modelQuery by remember { mutableStateOf("") }
    var years by remember { mutableStateOf<CatalogResult<List<Int>>?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    var variants by remember { mutableStateOf<CatalogResult<List<VehicleSpecVariant>>?>(null) }
    // Bumped by "Try again" to rerun whichever step failed.
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        if (makes !is CatalogResult.Found) {
            makes = null
            makes = catalog.carMakes()
        }
    }
    LaunchedEffect(make, attempt) {
        models = null
        make?.let { models = catalog.carModels(it.key) }
    }
    LaunchedEffect(make, model, attempt) {
        years = null
        val mk = make ?: return@LaunchedEffect
        model?.let { years = catalog.carYears(mk.key, it.key) }
    }
    LaunchedEffect(make, model, year, attempt) {
        variants = null
        val mk = make ?: return@LaunchedEffect
        val md = model ?: return@LaunchedEffect
        year?.let { variants = catalog.carVariants(mk.key, md.key, it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Step(makes, onRetry = { attempt++ }) { list ->
            SuggestField(
                label = stringResource(R.string.lookup_make),
                icon = Icons.Default.Business,
                query = makeQuery,
                selected = make,
                options = list,
                onQuery = { makeQuery = it; make = null; model = null; modelQuery = ""; year = null },
                onSelect = { make = it; makeQuery = it.name; model = null; modelQuery = ""; year = null }
            )
        }
        if (make != null) {
            Step(models, onRetry = { attempt++ }) { list ->
                SuggestField(
                    label = stringResource(R.string.lookup_model),
                    icon = Icons.Default.DirectionsCar,
                    query = modelQuery,
                    selected = model,
                    options = list,
                    showAllWhenEmpty = true,
                    onQuery = { modelQuery = it; model = null; year = null },
                    onSelect = { model = it; modelQuery = it.name; year = null }
                )
            }
        }
        if (model != null) {
            Step(years, onRetry = { attempt++ }) { list ->
                if (list.isEmpty()) {
                    Notice(stringResource(R.string.lookup_no_results))
                } else {
                    Text(stringResource(R.string.creator_year), style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        list.forEach { y -> ToggleChip(y.toString(), y == year, { year = y }) }
                    }
                }
            }
        }
        val chosenYear = year
        if (chosenYear != null) {
            Step(variants, onRetry = { attempt++ }) { list ->
                VariantList(list) { onPicked(it, chosenYear) }
            }
        }
    }
}

/** Make and model typed in, year optional, then a search: the catalogue matches partial names. */
@Composable
private fun BikeLookup(catalog: VehicleCatalog, onPicked: (VehicleSpecVariant) -> Unit) {
    var make by remember { mutableStateOf("") }
    var makeChosen by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<CatalogResult<List<VehicleSpecVariant>>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        if (make.isBlank() || model.isBlank() || searching) return
        searching = true
        scope.launch {
            results = catalog.motorcycles(make, model, year.toIntOrNull())
            searching = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val bikeOptions = remember { BikeMakes.map { VehicleCatalogOption(it, it) } }
        SuggestField(
            label = stringResource(R.string.lookup_make),
            icon = Icons.Default.Business,
            query = make,
            selected = if (makeChosen) VehicleCatalogOption(make, make) else null,
            options = bikeOptions,
            onQuery = { make = it; makeChosen = false; results = null },
            onSelect = { make = it.name; makeChosen = true; results = null }
        )
        CustomTextField(stringResource(R.string.lookup_model), model, leadingIcon = Icons.Default.TwoWheeler) {
            model = it; results = null
        }
        CustomTextField(stringResource(R.string.lookup_year_optional), year, true, Icons.Default.Event) {
            year = it.filter(Char::isDigit).take(4); results = null
        }
        PrimaryButton(
            text = stringResource(if (searching) R.string.lookup_loading else R.string.lookup_search),
            onClick = ::search,
            enabled = make.isNotBlank() && model.isNotBlank() && !searching,
            accent = TrackProTheme.colors.fieldLive,
            modifier = Modifier.fillMaxWidth()
        )
        results?.let { result ->
            Step(result, onRetry = ::search) { list -> VariantList(list, onPicked) }
        }
    }
}

/** Renders a step's result: a spinner while it loads, a notice when it failed, else [content]. */
@Composable
private fun <T> Step(
    result: CatalogResult<T>?,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    when (result) {
        null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.sm)) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = TrackProTheme.colors.accent
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.lookup_loading), style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        }
        is CatalogResult.Found -> content(result.value)
        CatalogResult.SignedOut -> Notice(stringResource(R.string.lookup_signed_out))
        CatalogResult.Unavailable -> Notice(stringResource(R.string.lookup_unavailable), onRetry)
        CatalogResult.Offline -> Notice(stringResource(R.string.lookup_offline), onRetry)
    }
}

/**
 * A text field that offers matching options beneath it while nothing is selected. With
 * [showAllWhenEmpty] the first few show before anything is typed, for short lists like a make's
 * models.
 */
@Composable
private fun SuggestField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    query: String,
    selected: VehicleCatalogOption?,
    options: List<VehicleCatalogOption>,
    onQuery: (String) -> Unit,
    onSelect: (VehicleCatalogOption) -> Unit,
    showAllWhenEmpty: Boolean = false
) {
    CustomTextField(label, query, leadingIcon = icon, onValueChange = onQuery)
    if (selected != null) return

    val needle = query.trim()
    if (needle.isEmpty() && !showAllWhenEmpty) return
    val matches = remember(options, needle) {
        options
            .filter { it.name.contains(needle, ignoreCase = true) }
            // Names that start with what was typed come first: for "a", Audi before Lancia.
            .sortedBy { !it.name.startsWith(needle, ignoreCase = true) }
            .take(MaxSuggestions)
    }
    if (matches.isEmpty()) {
        if (needle.isNotEmpty()) Notice(stringResource(R.string.lookup_no_results))
        return
    }
    OptionList {
        matches.forEach { option ->
            OptionRow(title = option.name, onClick = { onSelect(option) })
        }
    }
}

/** The versions of the chosen vehicle, each with what tells it apart. */
@Composable
private fun VariantList(variants: List<VehicleSpecVariant>, onPick: (VehicleSpecVariant) -> Unit) {
    if (variants.isEmpty()) {
        Notice(stringResource(R.string.lookup_no_results))
        return
    }
    Text(stringResource(R.string.lookup_variants), style = TrackProType.label, color = TrackProTheme.colors.textMuted)
    OptionList {
        variants.forEach { variant ->
            OptionRow(
                title = variantTitle(variant),
                detail = variantDetail(variant),
                onClick = { onPick(variant) }
            )
        }
    }
}

@Composable
private fun variantTitle(v: VehicleSpecVariant): String = when {
    v.partial -> listOfNotNull(v.generation, v.fuel?.let { specLabel(it) }).joinToString(" · ").ifEmpty { v.model }
    v.yearFrom != null && v.yearFrom == v.yearTo -> "${v.name} (${v.yearFrom})"
    else -> v.name
}

@Composable
private fun variantDetail(v: VehicleSpecVariant): String? = listOfNotNull(
    v.powerHp?.let { stringResource(R.string.lookup_hp, it) },
    v.generation.takeIf { !v.partial },
    v.fuel.takeIf { !v.partial }?.let { specLabel(it) },
    v.transmission?.let { specLabel(it) },
    v.drivetrain,
).joinToString(" · ").ifEmpty { null }

/** What was filled in, with a way back to the search. */
@Composable
private fun PickedSummary(variant: VehicleSpecVariant, year: Int?, onChange: () -> Unit) {
    val name = listOfNotNull(variant.make, variant.model, year?.let { "($it)" }).joinToString(" ")
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackProShapes.chip)
            .background(TrackProTheme.colors.accentSoft)
            .padding(Spacing.md)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TrackProTheme.colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.lookup_filled, name), style = TrackProType.body, color = TrackProTheme.colors.textPrimary)
            if (variant.partial) {
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.lookup_partial), style = TrackProType.body, color = TrackProTheme.colors.textMuted)
            }
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        stringResource(R.string.lookup_change),
        style = TrackProType.titleMedium,
        color = TrackProTheme.colors.accent,
        modifier = Modifier
            .clip(TrackProShapes.chip)
            .pressableRow(onClick = onChange)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
    )
}

@Composable
private fun OptionList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackProShapes.chip)
            .background(TrackProTheme.colors.fieldLive)
    ) { content() }
}

@Composable
private fun OptionRow(title: String, onClick: () -> Unit, detail: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(title, style = TrackProType.titleMedium, color = TrackProTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(detail, style = TrackProType.body, color = TrackProTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A one-line explanation in place of a step, optionally with a retry. */
@Composable
private fun Notice(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Text(text, style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        if (onRetry != null) {
            Text(
                stringResource(R.string.lookup_retry),
                style = TrackProType.titleMedium,
                color = TrackProTheme.colors.accent,
                modifier = Modifier
                    .clip(TrackProShapes.chip)
                    .pressableRow(onClick = onRetry)
                    .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
            )
        }
    }
}
