# TrackPro
[![Github All Releases](https://img.shields.io/github/downloads/aredarn/trackpro/total.svg)]()

TrackPro is an open-source mobile app that grants automotive enthusiasts, racers, and anyone looking to enhance their driving experience—with real-time performance stats.

TrackPro, combined with any ESP32 or ESP8266 MCU and a GPS module, allows users to accurately measure and analyze their driving behavior.
The communication works with Wi-Fi using TCP for getting the best and most reliable results.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/drive.png" width="200" alt="Drive"><br><sub>Drive</sub></td>
    <td align="center"><img src="docs/screenshots/session.png" width="200" alt="Session summary"><br><sub>Session summary</sub></td>
    <td align="center"><img src="docs/screenshots/lap-heatmap.png" width="200" alt="Lap speed map"><br><sub>Lap speed map</sub></td>
    <td align="center"><img src="docs/screenshots/history.png" width="200" alt="History"><br><sub>History</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/garage.png" width="200" alt="Garage"><br><sub>Garage</sub></td>
    <td align="center"><img src="docs/screenshots/car.png" width="200" alt="Car"><br><sub>Car</sub></td>
    <td align="center"><img src="docs/screenshots/track.png" width="200" alt="Track"><br><sub>Track</sub></td>
    <td align="center"><img src="docs/screenshots/profile.png" width="200" alt="Profile"><br><sub>Profile</sub></td>
  </tr>
</table>

Key Features:
- Lap Timing: Measures lap times for circuit racing, karting, or track days.

- Track builder: make your own track, store it and mesure your speed and times
  
- Drag Timer: ¼-mile, 0-60 mph / 0-100Kmh or other custom drag race metrics
  
- Acceleration Analysis: Analyze acceleration data over time as well as distance for per
formance evaluation.
  
- Breaking Stats: Track and review braking efficiency and metrics such as stopping distance and deceleration rates.

- Add your vehicle: Save your car, bike or other vehicle and give their stats and use them sessions

Future plans:
- Live sharing
- Online leaderboard
- Data-to-video
- Checkpoint time stat / sprint race

Main menu:

GPS Connection testing:
  This page is responsible for testing the connection between the ESP's Wifi and the Application.
  The proper software and hardware is required for the ESP whic can be found here: https://github.com/Aredarn/TrackPro_ESP
  The following can be seen if the connection is successful and the ESP's GPS has position lock.
    - Latitude
    - Longitude
    - Altitude (In meters)
    - Satellites
    - Speed (in kilometer/hour)
    - Timestamp

<img width="30%" alt="GPS connection test" src="docs/screenshots/gps-connection.png" />

Add your own car:

<img width="30%" alt="Add car" src="docs/screenshots/add-car.png" />

Drag-time screen:

<img width="30%" alt="Drag timer" src="docs/screenshots/drag-timer.png" />

Lap timer screen:

<img width="30%" alt="Lap timer" src="docs/screenshots/lap-timer.png" />

Lap builder screen:

<img width="30%" alt="Track builder" src="docs/screenshots/track-builder.png" />
