INSERT INTO poi_type (code, name, icon)
VALUES ('landmark', 'Достопримечательность', 'landmark-icon')
ON CONFLICT (code) DO NOTHING;