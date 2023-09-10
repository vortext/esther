import re
from iso6709 import Location
import json

def parse_file(file_path):
    result = {}
    with open(file_path, 'r', encoding='utf-8') as file:
        for line in file:
            if not line.startswith("#"):
                columns = line.strip().split("\t")
                if len(columns) >= 3:
                    country_codes, coordinates, timezone = columns[:3]
                    location = Location(coordinates)
                    obj = {
                        "country_codes": country_codes.split(","),
                        "coordinates_decimal": {"lat": float(location.lat.decimal),
                                                "lon": float(location.lng.decimal)},
                        "coordinates_iso6709": coordinates,
                        "timezone": timezone
                    }
                    result[timezone] = obj
    print(json.dumps(result, indent=2))

if __name__ == "__main__":
    parse_file('zone1970.tab')  # Replace with the actual path to your file
