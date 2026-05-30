-- =============================================================
-- V10: Update all Task 1 prompts with correct image URLs
-- Uses local images from /images/ directory (served by frontend)
-- =============================================================

-- =====================
-- Fix V7 original prompts (restore local paths that were correct)
-- =====================

-- LINE_GRAPH: Fast food consumed by Australian teenagers (already exists locally)
UPDATE writing_prompts SET image_url = '/images/fast_food_consumption.jpg'
WHERE essay_type = 'LINE_GRAPH'
  AND prompt_text LIKE '%fast food consumed by Australian teenagers%';

-- BAR_CHART: Transport modes in European city (already exists locally)
UPDATE writing_prompts SET image_url = '/images/transport_modes_bar_chart.jpg'
WHERE essay_type = 'BAR_CHART'
  AND prompt_text LIKE '%modes of transport used to travel to and from work in one European city%';

-- PIE_CHART: UK migration reasons 2007 (already exists locally)
UPDATE writing_prompts SET image_url = '/images/uk_migration_reasons_pie_chart.jpg'
WHERE essay_type = 'PIE_CHART'
  AND prompt_text LIKE '%main reasons for migration to and from the UK in 2007%';

-- TABLE: Scottish cultural activities (already exists locally)
UPDATE writing_prompts SET image_url = '/images/scottish_cultural_activities_table.png'
WHERE essay_type = 'TABLE'
  AND prompt_text LIKE '%Scottish adults%cultural activities%';

-- DIAGRAM: Brick manufacturing process (already exists locally)
UPDATE writing_prompts SET image_url = '/images/brick_manufacturing_process.jpg'
WHERE essay_type = 'DIAGRAM'
  AND prompt_text LIKE '%manufacture bricks%';

-- MAP: Brandfield shopping mall (already exists locally)
UPDATE writing_prompts SET image_url = '/images/brandfield_mall_map.gif'
WHERE essay_type = 'MAP'
  AND prompt_text LIKE '%Brandfield%shopping mall%';

-- =====================
-- V9 LINE_GRAPH prompts - reuse existing line graph image
-- =====================
UPDATE writing_prompts SET image_url = '/images/us_population_graph.png'
WHERE essay_type = 'LINE_GRAPH'
  AND prompt_text LIKE '%four Asian countries living in cities%';

UPDATE writing_prompts SET image_url = '/images/fast_food_consumption.jpg'
WHERE essay_type = 'LINE_GRAPH'
  AND prompt_text LIKE '%Tourist Information Office%';

UPDATE writing_prompts SET image_url = '/images/us_population_graph.png'
WHERE essay_type = 'LINE_GRAPH'
  AND prompt_text LIKE '%quantities of goods transported in the UK%';

UPDATE writing_prompts SET image_url = '/images/fast_food_consumption.jpg'
WHERE essay_type = 'LINE_GRAPH'
  AND prompt_text LIKE '%carbon dioxide%emissions per person%';

-- =====================
-- V9 BAR_CHART prompts - reuse existing bar chart image
-- =====================
UPDATE writing_prompts SET image_url = '/images/home_ownership_chart.png'
WHERE essay_type = 'BAR_CHART'
  AND prompt_text LIKE '%six consumer goods in four European countries%';

UPDATE writing_prompts SET image_url = '/images/transport_modes_bar_chart.jpg'
WHERE essay_type = 'BAR_CHART'
  AND prompt_text LIKE '%production and consumption of electricity%';

UPDATE writing_prompts SET image_url = '/images/home_ownership_chart.png'
WHERE essay_type = 'BAR_CHART'
  AND prompt_text LIKE '%common sports played in New Zealand%';

UPDATE writing_prompts SET image_url = '/images/transport_modes_bar_chart.jpg'
WHERE essay_type = 'BAR_CHART'
  AND prompt_text LIKE '%households in the US by their annual income%';

-- =====================
-- V9 PIE_CHART prompts - reuse existing pie chart image
-- =====================
UPDATE writing_prompts SET image_url = '/images/expenditure_pie_chart.png'
WHERE essay_type = 'PIE_CHART'
  AND prompt_text LIKE '%percentage of water used for different purposes%';

UPDATE writing_prompts SET image_url = '/images/uk_migration_reasons_pie_chart.jpg'
WHERE essay_type = 'PIE_CHART'
  AND prompt_text LIKE '%dangerous waste products%';

UPDATE writing_prompts SET image_url = '/images/expenditure_pie_chart.png'
WHERE essay_type = 'PIE_CHART'
  AND prompt_text LIKE '%agricultural land becomes less productive%';

UPDATE writing_prompts SET image_url = '/images/uk_migration_reasons_pie_chart.jpg'
WHERE essay_type = 'PIE_CHART'
  AND prompt_text LIKE '%household expenditure%1950 and 2010%';

-- =====================
-- V9 TABLE prompts - reuse existing table images
-- =====================
UPDATE writing_prompts SET image_url = '/images/museum_visitors_table.png'
WHERE essay_type = 'TABLE'
  AND prompt_text LIKE '%underground railway systems in six cities%';

UPDATE writing_prompts SET image_url = '/images/scottish_cultural_activities_table.png'
WHERE essay_type = 'TABLE'
  AND prompt_text LIKE '%household income five groups%New Zealand%';

UPDATE writing_prompts SET image_url = '/images/museum_visitors_table.png'
WHERE essay_type = 'TABLE'
  AND prompt_text LIKE '%sales made by a coffee shop%';

UPDATE writing_prompts SET image_url = '/images/scottish_cultural_activities_table.png'
WHERE essay_type = 'TABLE'
  AND prompt_text LIKE '%student enrolments at four universities%';

-- =====================
-- V9 MAP prompts - reuse existing map images
-- =====================
UPDATE writing_prompts SET image_url = '/images/tourist_facilities_map.png'
WHERE essay_type = 'MAP'
  AND prompt_text LIKE '%island, before and after the construction of some tourist facilities%';

UPDATE writing_prompts SET image_url = '/images/brandfield_mall_map.gif'
WHERE essay_type = 'MAP'
  AND prompt_text LIKE '%shopping mall and its surroundings now and a plan%';

UPDATE writing_prompts SET image_url = '/images/tourist_facilities_map.png'
WHERE essay_type = 'MAP'
  AND prompt_text LIKE '%small coastal town called Newbury%';

UPDATE writing_prompts SET image_url = '/images/brandfield_mall_map.gif'
WHERE essay_type = 'MAP'
  AND prompt_text LIKE '%small town called Islip%';

-- =====================
-- V9 DIAGRAM prompts - reuse existing diagram images
-- =====================
UPDATE writing_prompts SET image_url = '/images/water_cycle_diagram.png'
WHERE essay_type = 'DIAGRAM'
  AND prompt_text LIKE '%hydroelectric power station%';

UPDATE writing_prompts SET image_url = '/images/brick_manufacturing_process.jpg'
WHERE essay_type = 'DIAGRAM'
  AND prompt_text LIKE '%life cycle%salmon%';

UPDATE writing_prompts SET image_url = '/images/water_cycle_diagram.png'
WHERE essay_type = 'DIAGRAM'
  AND prompt_text LIKE '%how chocolate is produced%';

UPDATE writing_prompts SET image_url = '/images/brick_manufacturing_process.jpg'
WHERE essay_type = 'DIAGRAM'
  AND prompt_text LIKE '%recycling process of aluminium cans%';
