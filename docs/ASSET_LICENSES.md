# Demo media licenses

The local WebP files under `frontend/public/assets/properties`, `destinations`, and
`room-types` are development/demo media. They were downloaded from Unsplash on
2026-07-15 and are subject to the [Unsplash License](https://unsplash.com/license).
They are not Agoda, Booking, Traveloka, or property-owner uploads.

## File-to-source mapping

| Local property file | Unsplash photo ID |
|---|---|
| `properties/hotel-city-01.webp` | `1542314831-068cd1dbfeeb` |
| `properties/hotel-city-02.webp` | `1566073771259-6a8506099945` |
| `properties/motel-01.webp` | `1582719478250-c89cae4dc85b` |
| `properties/homestay-01.webp` | `1578683010236-d716f9a3f461` |
| `properties/hostel-01.webp` | `1611892440504-42a792e24d32` |
| `properties/apartment-01.webp` | `1590490360182-c33d57733427` |
| `properties/villa-01.webp` | `1600607687920-4e2a09cf159d` |
| `properties/resort-01.webp` | `1600047509807-ba8f99d2cdde` |
| `properties/guest-house-01.webp` | `1520250497591-112f2f40a3f4` |
| `properties/hotel-beach-01.webp` | `1566665797739-1674de7a421a` |
| `properties/hotel-room-01.webp` | `1564501049412-61c2a3083791` |
| `properties/hotel-room-02.webp` | `1598928636135-d146006ff4be` |

| Local destination file | Unsplash photo ID |
|---|---|
| `destinations/destination-01.webp` | `1528127269322-539801943592` |
| `destinations/destination-02.webp` | `1559592413-7cec4d0cae2b` |
| `destinations/destination-03.webp` | `1583417319070-4a69db38a482` |
| `destinations/destination-04.webp` | `1528181304800-259b08848526` |
| `destinations/destination-05.webp` | `1557750255-c76072a7aad1` |
| `destinations/destination-06.webp` | `1573790387438-4da905039392` |
| `destinations/destination-07.webp` | `1531737212413-667205e1cda7` |
| `destinations/destination-08.webp` | `1509030450996-dd1a26dda07a` |

| Local room file | Unsplash photo ID |
|---|---|
| `room-types/single-room-01.webp` | `1560448204-e02f11c3d0e2` |
| `room-types/double-room-01.webp` | `1591088398332-8a7791972843` |
| `room-types/double-room-02.webp` | `1560185008-b033106af5c3` |
| `room-types/twin-room-01.webp` | `1616594039964-ae9021a400a0` |
| `room-types/family-room-01.webp` | `1618773928121-c32242e63f39` |
| `room-types/suite-room-01.webp` | `1631049307264-da0ec9d70304` |
| `room-types/suite-room-02.webp` | `1595576508898-0ad5c879a061` |

Fallback files are byte-identical local copies selected by property, destination,
or room type:

| Local fallback file | Copied licensed file |
|---|---|
| `fallbacks/destination-default.webp` | `destinations/destination-01.webp` |
| `fallbacks/hotel-default.webp` | `properties/hotel-city-01.webp` |
| `fallbacks/motel-default.webp` | `properties/motel-01.webp` |
| `fallbacks/homestay-default.webp` | `properties/homestay-01.webp` |
| `fallbacks/hostel-default.webp` | `properties/hostel-01.webp` |
| `fallbacks/apartment-default.webp` | `properties/apartment-01.webp` |
| `fallbacks/villa-default.webp` | `properties/villa-01.webp` |
| `fallbacks/resort-default.webp` | `properties/resort-01.webp` |
| `fallbacks/guest-house-default.webp` | `properties/guest-house-01.webp` |
| `fallbacks/single-room-default.webp` | `room-types/single-room-01.webp` |
| `fallbacks/double-room-default.webp` | `room-types/double-room-01.webp` |
| `fallbacks/twin-room-default.webp` | `room-types/twin-room-01.webp` |
| `fallbacks/family-room-default.webp` | `room-types/family-room-01.webp` |
| `fallbacks/suite-default.webp` | `room-types/suite-room-01.webp` |

They are only displayed for null, blank, or failed media URLs and retain the
source photo ID of the copied file.
