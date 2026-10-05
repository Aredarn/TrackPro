package com.example.trackpro.online

import java.util.Locale

/**
 * Messages that reach the screen in English, shown in the app's language.
 *
 * Two sources write them. TrackBoard answers in English, and the sync layer's own texts
 * ("Could not reach the TrackBoard server.") are part of its tested behaviour and are also
 * stored - a car's last sync error lives in the database. Translating where they are made
 * would need a Context in code that has none and would break both. So they stay English
 * where they are made and stored, and are translated here, at the point they are shown.
 *
 * A message is matched whole, line by line. Parts a pattern captures (a car's name, a
 * server reason) are translated again in case they are messages themselves. Anything not
 * listed is shown as it came - a server reason nobody has translated yet is still better
 * than nothing.
 */
object MessageText {

    fun localize(message: String?): String? {
        if (message == null || Locale.getDefault().language != "hu") return message
        return message.lines().joinToString("\n") { translate(it) }
    }

    private fun translate(line: String): String {
        val text = line.trim()
        for ((pattern, template) in HUNGARIAN) {
            val match = pattern.matchEntire(text) ?: continue
            var out = template
            // Highest group first, so $1 never eats the start of $10.
            for (i in match.groupValues.lastIndex downTo 1) {
                out = out.replace("$$i", translate(match.groupValues[i]))
            }
            return out
        }
        return line
    }

    private fun p(regex: String, hungarian: String) = Regex(regex) to hungarian

    private val HUNGARIAN: List<Pair<Regex, String>> = listOf(
        // ── Transport and server status (TrackBoardApi) ─────────────────────
        p("""Could not reach the TrackBoard server\.""", "Nem sikerült elérni a TrackBoard szervert."),
        p("""No TrackBoard server is set\. Add one in Settings\.""", "Nincs beállítva TrackBoard szerver. Add meg a Beállításokban."),
        p("""No TrackBoard server is set\.""", "Nincs beállítva TrackBoard szerver."),
        p("""The TrackBoard server address is not a valid URL\.""", "A TrackBoard szerver címe nem érvényes URL."),
        p("""The server returned an unusable upload address\.""", "A szerver használhatatlan feltöltési címet adott."),
        p("""Photo storage refused the upload \((\d+)\)\.""", "A fotótár visszautasította a feltöltést ($1)."),
        p("""Could not download a photo \((\d+)\)\.""", "Nem sikerült letölteni egy fotót ($1)."),
        p("""Could not reach photo storage\.""", "Nem sikerült elérni a fotótárat."),
        p("""Not signed in, or the sign-in has expired\.""", "Nem vagy bejelentkezve, vagy lejárt a bejelentkezés."),
        p("""That belongs to another account\.""", "Ez egy másik fiókhoz tartozik."),
        p("""Not found on the server\.""", "Nem található a szerveren."),
        p("""Too many attempts\. Wait a minute and try again\.""", "Túl sok próbálkozás. Várj egy percet, és próbáld újra."),
        p("""The TrackBoard server had a problem\. Try again later\.""", "Hiba történt a TrackBoard szerveren. Próbáld újra később."),
        p("""The server refused the request \((\d+)\)\.""", "A szerver elutasította a kérést ($1)."),
        p("""Not signed in\.""", "Nem vagy bejelentkezve."),
        p("""Not signed in to TrackBoard\.""", "Nem vagy bejelentkezve a TrackBoardba."),

        // ── Sync, restore and garage ─────────────────────────────────────────
        p("""Something could not be restored\.""", "Valamit nem sikerült visszaállítani."),
        p("""Something could not be synced\.""", "Valamit nem sikerült szinkronizálni."),
        p("""A vehicle could not be synced\.""", "Egy autót nem sikerült szinkronizálni."),
        p("""Could not read your tracks from TrackBoard: (.*)""", "Nem sikerült beolvasni a pályáidat a TrackBoardról: $1"),
        p("""Could not read your sessions from TrackBoard: (.*)""", "Nem sikerült beolvasni a meneteidet a TrackBoardról: $1"),
        p("""Could not read (.+) from TrackBoard: (.*)""", "Nem sikerült beolvasni a TrackBoardról ($1): $2"),
        p(""""(.+)" can't be published: it needs at least two valid track points\.""", "A(z) „$1” nem tehető közzé: legalább két érvényes pályapont kell hozzá."),
        p(""""(.+)" can't be shared: its bundled geometry is incomplete\.""", "A(z) „$1” nem osztható meg: a beépített nyomvonala hiányos."),
        p(""""(.+)" isn't available on TrackBoard right now\.""", "A(z) „$1” most nem érhető el a TrackBoardon."),
        p("""This account has reached its limit\.""", "Ez a fiók elérte a korlátját."),
        p("""The server rejected it\.""", "A szerver nem fogadta el."),
        p("""The server rejected (.+)\.""", "A szerver nem fogadta el: $1."),
        p("""(.+) is missing details the account needs, so it stays on this phone\.""", "A(z) $1 adataiból hiányzik, ami a fiókhoz kell, ezért ezen a telefonon marad."),
        p("""The account refused (.+)\.""", "A fiók nem fogadta el: $1."),
        p("""Removed from your account on another device\. Kept on this phone\.""", "Egy másik eszközön törölted a fiókodból. Ezen a telefonon megmaradt."),
        p("""This server does not store photos, so they stay on this phone\.""", "Ez a szerver nem tárol fotókat, ezért azok ezen a telefonon maradnak."),
        p("""The photo of (.+) could not be synced: (.*)""", "A(z) $1 fotóját nem sikerült szinkronizálni: $2"),
        p("""That track is no longer published on TrackBoard\.""", "Ez a pálya már nincs közzétéve a TrackBoardon."),
        p("""That track has no usable outline\.""", "Ennek a pályának nincs használható nyomvonala."),

        // ── Profile and account ──────────────────────────────────────────────
        p("""Offline\. Showing what was last synced\.""", "Nincs kapcsolat. A legutóbb szinkronizált adatok látszanak."),
        p("""This server has no profiles yet\. Showing this phone's records\.""", "Ezen a szerveren még nincsenek profilok. A telefon saját adatai látszanak."),
        p("""That file could not be read as a picture\.""", "Ezt a fájlt nem sikerült képként beolvasni."),
        p("""Could not write to the chosen file\.""", "Nem sikerült írni a kiválasztott fájlba."),
        p("""Your TrackBoard account was deleted\. Everything on this phone is still here\.""", "A TrackBoard-fiókod törlődött. A telefonon minden megmaradt."),
        p("""Your TrackBoard sign-in expired\. Sign in again\.""", "Lejárt a TrackBoard-bejelentkezésed. Jelentkezz be újra."),

        // ── The rig's link layer ─────────────────────────────────────────────
        p("""Bluetooth permission not granted""", "Nincs Bluetooth-engedély"),
        p("""No Bluetooth device selected""", "Nincs kiválasztott Bluetooth-eszköz"),
        p("""Bluetooth not supported on this device""", "Ez az eszköz nem támogatja a Bluetooth-t"),
        p("""Bluetooth connect timed out after (\d+)ms""", "A Bluetooth-kapcsolódás $1 ms után időtúllépéssel leállt"),
        p("""Link failed""", "A kapcsolat megszakadt"),

        // ── TrackBoard's own answers ─────────────────────────────────────────
        p("""Email or password is incorrect\.""", "Hibás e-mail-cím vagy jelszó."),
        p("""The password is incorrect\.""", "Hibás jelszó."),
        p("""An account with that email already exists\.""", "Ezzel az e-mail-címmel már van fiók."),
        p("""Passwords must be at least 12 characters\.""", "A jelszónak legalább 12 karakterből kell állnia."),
        p("""A display name needs at least two visible characters\.""", "A megjelenített névhez legalább két látható karakter kell."),
        p("""Too many failed attempts\. Try again in (\d+) minutes\.""", "Túl sok sikertelen próbálkozás. Próbáld újra $1 perc múlva."),
        p("""This account no longer exists\.""", "Ez a fiók már nem létezik."),
        p("""The refresh token is not valid\.""", "A bejelentkezés már nem érvényes."),
        p("""Rate limit exceeded\..*""", "Túl sok kérés. Várj egy kicsit, és próbáld újra."),
        p("""Too many requests""", "Túl sok kérés"),
        p("""The request could not be completed\. Contact support with the trace identifier\.""", "A kérést nem sikerült teljesíteni. Fordulj a támogatáshoz a nyomkövetési azonosítóval."),
        p("""A track needs at least two points\.""", "Egy pályához legalább két pont kell."),
        p("""This track already has ranked laps, so its points can no longer change\.""", "Ezen a pályán már vannak rangsorolt körök, ezért a pontjai nem változhatnak."),
        p("""Lap number (\d+) appears more than once\.""", "A(z) $1. kör többször szerepel."),
        p("""Lap (\d+) lists sector (\d+) more than once\.""", "A(z) $1. körben a(z) $2. szektor többször szerepel."),
        p("""Point seq (\d+) appears more than once\.""", "A(z) $1. pont többször szerepel."),
        p("""Other drivers have sessions on this track, so it cannot be deleted\.""", "Más pilóták is futottak ezen a pályán, ezért nem törölhető."),
        p("""Other drivers have sessions on this track, so it cannot be made private\.""", "Más pilóták is futottak ezen a pályán, ezért nem tehető priváttá."),
        p("""An event runs on this track, so it cannot be deleted\. Delete the event first\.""", "Ezen a pályán esemény fut, ezért nem törölhető. Előbb az eseményt töröld."),
        p("""This vehicle is used by uploaded sessions and cannot be deleted\.""", "Ehhez az autóhoz feltöltött menetek tartoznak, ezért nem törölhető."),
        p("""The vehicle does not belong to this driver\.""", "Az autó nem ehhez a pilótához tartozik."),
        p("""Photo storage is not configured on this server\.""", "Ezen a szerveren nincs beállítva fotótár."),
        p("""Photo storage refused the upload\. Check that the media bucket exists\.""", "A fotótár visszautasította a feltöltést."),
        p("""Photo storage returned an empty answer\.""", "A fotótár üres választ adott."),
        p("""That photo was not uploaded for this profile\.""", "Ez a fotó nem ehhez a profilhoz lett feltöltve."),
        p("""contentType must be image/jpeg or image/webp\.""", "Csak JPEG vagy WebP kép tölthető fel."),
        p("""An event needs a track\.""", "Az eseményhez pálya kell."),
        p("""An event can last at most 7 days\.""", "Egy esemény legfeljebb 7 napig tarthat."),
        p("""Only the event's host can do that\.""", "Ezt csak az esemény szervezője teheti meg."),
        p("""This event has finished, so it can no longer be joined\.""", "Ez az esemény véget ért, ezért már nem lehet csatlakozni."),
        p("""Could not find a free join code\.""", "Nem sikerült szabad csatlakozási kódot találni."),
        p("""You have reached the limit of (\d+) (.+)\. Delete one before adding another\.""", "Elérted a korlátot ($1 db). Törölj egyet, mielőtt újat adsz hozzá."),
        p("""The (.+) field is required\.""", "A(z) $1 mező kitöltése kötelező."),
        p("""The (.+) field is not a valid e-mail address\.""", "A(z) $1 mező nem érvényes e-mail-cím."),
    )
}
