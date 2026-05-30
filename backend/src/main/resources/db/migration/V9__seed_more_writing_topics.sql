-- =============================================================
-- V9: Seed additional Writing prompts for all essay types
-- Realistic IELTS exam topics from Cambridge, British Council, and IDP sources
-- =============================================================

-- Widen essay_type column to accommodate new longer type names
ALTER TABLE writing_prompts MODIFY COLUMN essay_type VARCHAR(50) NOT NULL;

-- =====================
-- TASK 2: OPINION (Agree/Disagree)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('Some people think that all university students should study whatever they like. Others believe that they should only be allowed to study subjects that will be useful in the future, such as those related to science and technology. Discuss both these views and give your own opinion.', 'OPINION', NULL),
('Some people believe that it is best to accept a bad situation, such as an unsatisfactory job or shortage of money. Others argue that it is better to try and improve such situations. Discuss both these views and give your own opinion.', 'OPINION', NULL),
('In the future, nobody will buy printed newspapers or books because they will be able to read everything they want online without paying. To what extent do you agree or disagree with this statement?', 'OPINION', NULL),
('Some people say that the main environmental problem of our time is the loss of particular species of plants and animals. Others say that there are more important environmental problems. Discuss both these views and give your own opinion.', 'OPINION', NULL),
('It is important for children to learn the difference between right and wrong at an early age. Punishment is necessary to help them learn this distinction. To what extent do you agree or disagree with this opinion?', 'OPINION', NULL),
('Universities should accept equal numbers of male and female students in every subject. To what extent do you agree or disagree?', 'OPINION', NULL),
('Some experts believe that it is better for children to begin learning a foreign language at primary school rather than secondary school. Do the advantages of this outweigh the disadvantages?', 'OPINION', NULL),
('Nowadays many people choose to be self-employed, rather than to work for a company or organisation. Why might this be the case? What could be the disadvantages of being self-employed?', 'OPINION', NULL),
('Some people think that parents should teach children how to be good members of society. Others, however, believe that school is the place to learn this. Discuss both these views and give your own opinion.', 'OPINION', NULL),
('Space exploration is much too expensive and the money should be spent on more important things. To what extent do you agree or disagree?', 'OPINION', NULL);

-- =====================
-- TASK 2: DISCUSSION
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('Some people believe that children should be taught to give presentations and speeches at school. Others disagree. Discuss both views and give your opinion.', 'DISCUSSION', NULL),
('Some people think that the government should provide free housing for people who cannot afford it. Others believe that the government should not be responsible for providing housing. Discuss both views and give your opinion.', 'DISCUSSION', NULL),
('Some people think that competitive sports have a positive effect on children''s education. Others believe that competitive sports have a negative effect. Discuss both views and give your opinion.', 'DISCUSSION', NULL),
('Some people think that strict punishments for driving offences are the key to reducing traffic accidents. Others, however, believe that other measures would be more effective in improving road safety. Discuss both these views and give your own opinion.', 'DISCUSSION', NULL),
('Some people think that the government should establish free libraries in each town. Others believe that it is a waste of money since people can access information from the internet at home. Discuss both views and give your own opinion.', 'DISCUSSION', NULL),
('Some people think that the best way to improve road safety is to increase the minimum legal age for driving cars or riding motorbikes. Others think there are more effective methods. Discuss both views and give your opinion.', 'DISCUSSION', NULL),
('Some people think that museums should be enjoyable places to entertain people, while others believe that the purpose of museums is to educate. Discuss both views and give your own opinion.', 'DISCUSSION', NULL),
('Some people think that wild animals should not be kept in zoos. Others believe that there are good reasons for having zoos. Discuss both these views and give your own opinion.', 'DISCUSSION', NULL),
('Some people say that economic growth is the only way to end hunger and poverty, while others say that economic growth is damaging the environment and should be stopped. Discuss both views and give your opinion.', 'DISCUSSION', NULL),
('Some people believe that the best way to produce a happier society is to ensure that there are only small differences between the richest and the poorest members. Others think that allowing people to be as rich as possible is a better approach. Discuss both views and give your opinion.', 'DISCUSSION', NULL);

-- =====================
-- TASK 2: CAUSE AND EFFECT
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('In many cities the use of video cameras in public places is being increased in order to reduce crime, but some people believe that these measures restrict our individual freedom. Do the benefits of increased security outweigh the drawbacks?', 'CAUSE_AND_EFFECT', NULL),
('An increasing number of people are buying what they need online. What are the advantages and disadvantages of shopping online for both customers and companies?', 'CAUSE_AND_EFFECT', NULL),
('More and more people are migrating to cities in search of a better life, but city life can be extremely difficult. Explain some of the difficulties of living in a city. How can governments make urban life better for everyone?', 'CAUSE_AND_EFFECT', NULL),
('Global warming is one of the most serious issues that the world is facing today. What are the causes of global warming and what measures can governments and individuals take to tackle the issue?', 'CAUSE_AND_EFFECT', NULL),
('The proportion of older people is steadily increasing in many countries. What problems will this cause for individuals and society? Suggest some measures that could be taken to reduce the impact of ageing populations.', 'CAUSE_AND_EFFECT', NULL),
('Plastic bags, plastic bottles and plastic packaging are bad for the environment. What damage does plastic do to the environment? What can be done by governments and individuals to solve this problem?', 'CAUSE_AND_EFFECT', NULL),
('In many countries, the gap between the rich and the poor is widening. What problems does this cause? What solutions can you suggest?', 'CAUSE_AND_EFFECT', NULL),
('Many young people today leave their own countries to work abroad. What are the reasons for this trend? What problems does it cause?', 'CAUSE_AND_EFFECT', NULL);

-- =====================
-- TASK 2: PROBLEM AND SOLUTION (NEW)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('In spite of the advances made in agriculture, many people around the world still go hungry. Why is this the case? What can be done about this problem?', 'PROBLEM_AND_SOLUTION', NULL),
('Many working people get little or no exercise either during the working day or in their free time, and have health problems as a result. Why do so many working people not get enough exercise? What can be done about this problem?', 'PROBLEM_AND_SOLUTION', NULL),
('Many people today find it difficult to balance their work with other parts of their lives. What are the reasons for this? What can individuals and employers do to solve this problem?', 'PROBLEM_AND_SOLUTION', NULL),
('In many countries around the world, rural people are moving to cities, so the population in the countryside is decreasing. What problems can this cause? What can be done about this situation?', 'PROBLEM_AND_SOLUTION', NULL),
('In many countries, people are now living longer than ever before. Some people say an ageing population creates problems for governments. What are the problems? What solutions can be offered?', 'PROBLEM_AND_SOLUTION', NULL),
('Crime rates tend to be higher in cities than in smaller towns. Explain some possible reasons for this problem and suggest some solutions.', 'PROBLEM_AND_SOLUTION', NULL),
('Many schools are now facing problems with the behaviour of students. What are the causes of this? What solutions can you suggest?', 'PROBLEM_AND_SOLUTION', NULL),
('Traffic and housing problems in major cities could be solved by moving large companies and factories and their employees to the countryside. What are the problems associated with this? What solutions can you suggest?', 'PROBLEM_AND_SOLUTION', NULL);

-- =====================
-- TASK 2: ADVANTAGES AND DISADVANTAGES (NEW)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('In some countries, many more people are choosing to live alone nowadays than in the past. Do you think this is a positive or negative development?', 'ADVANTAGES_DISADVANTAGES', NULL),
('In some countries, an increasing number of people are choosing to have their first child at an older age. What are the advantages and disadvantages of this trend?', 'ADVANTAGES_DISADVANTAGES', NULL),
('Some people think that it is more effective for students to study in groups, while others believe that it is better for them to study alone. Discuss the advantages and disadvantages of both approaches.', 'ADVANTAGES_DISADVANTAGES', NULL),
('In some cultures, children are often told that they can achieve anything if they try hard enough. What are the advantages and disadvantages of giving children this message?', 'ADVANTAGES_DISADVANTAGES', NULL),
('Many employers now offer their staff the option of working from home for part or all of the working week. What are the advantages and disadvantages of working from home for employers and employees?', 'ADVANTAGES_DISADVANTAGES', NULL),
('More and more people are using the internet to do their shopping. What are the advantages and disadvantages of shopping online?', 'ADVANTAGES_DISADVANTAGES', NULL),
('Some cities have introduced car-free zones in their centres. What are the advantages and disadvantages of this measure?', 'ADVANTAGES_DISADVANTAGES', NULL),
('Some countries are introducing laws to limit the working hours of employees. What are the advantages and disadvantages of such a measure?', 'ADVANTAGES_DISADVANTAGES', NULL);

-- =====================
-- TASK 2: TWO-PART QUESTION / DIRECT QUESTIONS (NEW)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('Today more people are overweight than ever before. What in your opinion are the primary causes of this? What are the main effects of this epidemic?', 'TWO_PART_QUESTION', NULL),
('Some people think that having a set retirement age (e.g. 65 years) for everybody, regardless of occupation, is unfair. They believe that certain workers deserve to retire and receive a pension at an earlier age. Do you agree or disagree? Which types of workers do you think should benefit from early retirement?', 'TWO_PART_QUESTION', NULL),
('Today, the high sales of popular consumer goods reflect the power of advertising and not the real needs of the society in which they are sold. To what extent do you agree or disagree? What changes can be made to advertising practices?', 'TWO_PART_QUESTION', NULL),
('Happiness is considered very important in life. Why is it difficult to define? What factors are important in achieving happiness?', 'TWO_PART_QUESTION', NULL),
('More and more people are becoming seriously overweight. Some people say that the price increase of fattening food will solve this problem. To what extent do you agree or disagree? What other measures do you think might be effective?', 'TWO_PART_QUESTION', NULL),
('Many old buildings are protected by law because they are part of a nation''s history. However, some people think they should be knocked down to make way for new ones. How important is it to maintain old buildings? Should history stand in the way of progress?', 'TWO_PART_QUESTION', NULL),
('Research indicates that the characteristics we are born with have much more influence on our personality and development than any experiences we may have in our life. Which do you consider to be the major influence? What kinds of experiences can influence personality development?', 'TWO_PART_QUESTION', NULL),
('Learning English at school is often seen as more important than learning local languages. If this is the case, why is this? What can be done to save local and regional languages from extinction?', 'TWO_PART_QUESTION', NULL);

-- =====================
-- TASK 1: LINE GRAPH (additional prompts)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('The graph below gives information about the percentage of the population in four Asian countries living in cities from 1970 to 2020, with predictions for 2030 and 2040. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'LINE_GRAPH', NULL),
('The graph below shows the number of enquiries received by the Tourist Information Office in one city over a six-month period in 2011. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'LINE_GRAPH', NULL),
('The graph below shows the quantities of goods transported in the UK between 1974 and 2002 by four different modes of transport. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'LINE_GRAPH', NULL),
('The line graph shows average carbon dioxide (CO2) emissions per person in the United Kingdom, Sweden, Italy and Portugal between 1967 and 2007. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'LINE_GRAPH', NULL);

-- =====================
-- TASK 1: BAR CHART (additional prompts)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('The chart below shows the amount spent on six consumer goods in four European countries. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'BAR_CHART', NULL),
('The bar chart below shows the top ten countries for the production and consumption of electricity in 2014. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'BAR_CHART', NULL),
('The chart below gives information about the most common sports played in New Zealand in 2002. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'BAR_CHART', NULL),
('The chart below shows the number of households in the US by their annual income in 2007, 2011 and 2015. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'BAR_CHART', NULL);

-- =====================
-- TASK 1: PIE CHART (additional prompts)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('The charts below show the percentage of water used for different purposes in six areas of the world. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'PIE_CHART', NULL),
('The pie charts below show how dangerous waste products are dealt with in three countries. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'PIE_CHART', NULL),
('The pie chart shows the main reasons why agricultural land becomes less productive. The table shows how these causes affected three regions of the world during the 1990s. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'PIE_CHART', NULL),
('The pie charts below compare household expenditure in a country in 1950 and 2010. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'PIE_CHART', NULL);

-- =====================
-- TASK 1: TABLE (additional prompts)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('The table below gives information about the underground railway systems in six cities. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'TABLE', NULL),
('The table below shows the percentage of household income five groups of people in New Zealand spent on different categories in 2006. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'TABLE', NULL),
('The table below shows the sales made by a coffee shop in an office building on a typical weekday. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'TABLE', NULL),
('The table below gives information about student enrolments at four universities in 2005, 2010 and 2015. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'TABLE', NULL);

-- =====================
-- TASK 1: MAP (additional prompts)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('The two maps below show an island, before and after the construction of some tourist facilities. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'MAP', NULL),
('The maps show a shopping mall and its surroundings now and a plan for its redevelopment. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'MAP', NULL),
('The two maps below show the changes that took place in a small coastal town called Newbury between 1950 and now. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'MAP', NULL),
('The maps below show the centre of a small town called Islip as it is now, and plans for its development. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'MAP', NULL);

-- =====================
-- TASK 1: DIAGRAM / PROCESS (additional prompts)
-- =====================
INSERT INTO writing_prompts (prompt_text, essay_type, image_url) VALUES
('The diagram below shows how electricity is generated in a hydroelectric power station. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'DIAGRAM', NULL),
('The diagram below shows the life cycle of a species of large fish called the salmon. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'DIAGRAM', NULL),
('The diagram below shows how chocolate is produced. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'DIAGRAM', NULL),
('The diagram below shows the recycling process of aluminium cans. Summarise the information by selecting and reporting the main features, and make comparisons where relevant.', 'DIAGRAM', NULL);
