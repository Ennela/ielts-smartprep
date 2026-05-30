-- Widen essay_type column to support new Task 1 types
ALTER TABLE writing_prompts MODIFY COLUMN essay_type VARCHAR(30) NOT NULL;

-- Add image_url column for Task 1 prompts (charts, diagrams, maps)
ALTER TABLE writing_prompts ADD COLUMN image_url VARCHAR(512) NULL;

-- Seed data: IELTS Writing Task 1 sample prompts with local chart images
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
(
  'The line graph below shows changes in the amount and type of fast food consumed by Australian teenagers from 1975 to 2000. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.',
  'LINE_GRAPH',
  '/images/fast_food_consumption.jpg'
),
(
  'The bar chart shows the different modes of transport used to travel to and from work in one European city in 1960, 1980 and 2000. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.',
  'BAR_CHART',
  '/images/transport_modes_bar_chart.jpg'
),
(
  'The pie charts show the main reasons for migration to and from the UK in 2007. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.',
  'PIE_CHART',
  '/images/uk_migration_reasons_pie_chart.jpg'
),
(
  'The Table below shows the results of a survey that asked 6800 Scottish adults (aged 16 years and over) whether they had taken part in different cultural activities in the past 12 months. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.',
  'TABLE',
  '/images/scottish_cultural_activities_table.png'
),
(
  'The diagram illustrates the process that is used to manufacture bricks for the building industry. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.',
  'DIAGRAM',
  '/images/brick_manufacturing_process.jpg'
),
(
  'Below is a map of the city of Brandfield. City planners have decided to build a new shopping mall for the area, and two sites, S1 and S2 have been proposed. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.',
  'MAP',
  '/images/brandfield_mall_map.gif'
);
