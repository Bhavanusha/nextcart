ALTER TABLE orders ADD COLUMN estimated_delivery_from DATE, ADD COLUMN estimated_delivery_to DATE;

UPDATE store_settings SET storefront_content = storefront_content || '{"heroImage":"/store-hero.webp"}'
WHERE id = 1 AND storefront_content->>'heroImage' = '/auth-interior.png';

UPDATE store_settings SET storefront_content = storefront_content || '{"storyImage":"/store-story.webp"}'
WHERE id = 1 AND storefront_content->>'storyImage' = '/auth-interior.png';

UPDATE store_settings SET storefront_content = '{
    "campaignTitle":"The Fall Edit",
    "campaignDescription":"Warm tones and natural textures for the changing seasons.",
    "campaignImage":"/store-campaign.webp"
}'::jsonb || storefront_content WHERE id = 1;
