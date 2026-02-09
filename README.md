# Battery Monitor Android App

An Android application that provides comprehensive battery monitoring capabilities through a WebView-based interface.

## Features

- Real-time battery status monitoring
- Battery health and temperature tracking  
- Power consumption analysis
- WebView-based user interface
- Location-aware battery usage tracking

## Requirements

- Android 5.0 (API level 21) or higher
- Required permissions:
  - Battery statistics access
  - Location access (for enhanced battery usage tracking)
  - Internet access

## Installation

1. Clone this repository
2. Open in Android Studio
3. Build and run the application

## Project Structure

- `app/src/main/java/com/deviant/batterymonitor/MainActivity.kt` - Main application logic
- `app/src/main/assets/index.html` - WebView interface for battery monitoring
- `app/src/main/AndroidManifest.xml` - App configuration and permissions

## Build Configuration

- Target SDK: 34 (Android 14)
- Min SDK: 21 (Android 5.0)
- Java Compatibility: Version 11

## Usage

Launch the app to view real-time battery information including:
- Current battery level
- Battery temperature
- Charging status
- Battery health
- Power consumption patterns