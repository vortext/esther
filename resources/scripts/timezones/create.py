from timezonefinder import TimezoneFinder
import json
import pandas as pd

# Download from https://simplemaps.com/data/world-cities
cities = pd.read_csv("worldcities.csv")

# Initialize the TimezoneFinder
tf = TimezoneFinder()

# Define a function to get the time zone for a given latitude and longitude
def get_timezone(row):
    return tf.timezone_at(lng=row['lng'], lat=row['lat'])

# Apply the function to each row in the DataFrame to get the time zone for each city
cities['timezone'] = cities.apply(get_timezone, axis=1)

cities = cities.dropna(subset=['population', 'timezone'])

cities = cities.rename(columns={'lat': 'latitude', 'lng': 'longitude'})

# Find the index of the city with the largest population in each time zone
idx = cities.groupby('timezone')['population'].idxmax()

# Create a new DataFrame with just the rows for the cities with the largest population in each time zone
timezone_city = cities.loc[idx]

# Select only the desired columns
timezone_city = timezone_city[['timezone', 'latitude', 'longitude', 'city', 'iso3']]

# Convert the DataFrame to a JSON object where the key is the timezone and the value is the rest of the object
timezone_city_json = timezone_city.set_index('timezone').to_dict('index')

# Save the JSON object to a file
with open('timezones.json', 'w') as f:
    json.dump(timezone_city_json, f, ensure_ascii=false, indent=2)
