import { MapContainer, TileLayer, Marker, Polyline, Popup } from 'react-leaflet'
import { Icon } from 'leaflet'
import { format } from 'date-fns'
import type { LocationPoint } from '../types'

// Custom violet marker
const violetIcon = new Icon({
  iconUrl: `data:image/svg+xml;base64,${btoa(`
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 36" width="24" height="36">
      <path d="M12 0C5.4 0 0 5.4 0 12c0 9 12 24 12 24s12-15 12-24C24 5.4 18.6 0 12 0z" fill="#3D2FA8"/>
      <circle cx="12" cy="12" r="5" fill="white"/>
    </svg>
  `)}`,
  iconSize: [24, 36],
  iconAnchor: [12, 36],
  popupAnchor: [0, -36],
})

interface LocationMapProps {
  locations: LocationPoint[]
  height?: number
}

export default function LocationMap({ locations, height = 400 }: LocationMapProps) {
  if (locations.length === 0) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F0EEF9', borderRadius: 12, color: '#6B7280' }}>
        no location data yet
      </div>
    )
  }

  const latest = locations[0]
  const center: [number, number] = [latest.latitude, latest.longitude]
  const trail: [number, number][] = locations.map((l) => [l.latitude, l.longitude])

  return (
    <div style={{ borderRadius: 12, overflow: 'hidden', height }}>
      <MapContainer center={center} zoom={15} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {/* Location trail */}
        {trail.length > 1 && (
          <Polyline positions={trail} color="#6C5CE7" weight={2} opacity={0.6} />
        )}
        {/* Current location */}
        <Marker position={center} icon={violetIcon}>
          <Popup>
            last seen {format(new Date(latest.created_at), 'h:mm a, MMM d')}
          </Popup>
        </Marker>
      </MapContainer>
    </div>
  )
}
