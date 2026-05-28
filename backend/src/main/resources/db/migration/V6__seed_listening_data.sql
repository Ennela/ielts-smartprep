-- =============================================
-- Seed data: 8 Listening Parts (2 per part_number)
-- =============================================

-- Part 1: Everyday social conversations
INSERT INTO listening_parts (part_number, title, topic, audio_url, transcript_text, duration_seconds) VALUES
(1, 'Hotel Reservation Phone Call', 'Accommodation', '/api/v1/listening/audio/part1a.mp3',
 'Receptionist: Good morning, Grand Palace Hotel. How may I help you?\nCaller: Hello, I would like to book a room for three nights, starting from the fifteenth of March.\nReceptionist: Certainly. We have a standard room at eighty-five pounds per night or a deluxe suite at one hundred and forty pounds.\nCaller: I will take the standard room, please.\nReceptionist: May I have your name?\nCaller: Yes, it is Catherine Williams. That is C-A-T-H-E-R-I-N-E, Williams.\nReceptionist: And a contact number?\nCaller: Zero seven seven zero, four five six, eight nine one two.\nReceptionist: We require a fifty-pound deposit. Would you like to pay by credit card?\nCaller: Yes, please. Visa card.\nReceptionist: Your booking reference is HP-3352. Check-in time is two p.m.\nCaller: Is breakfast included?\nReceptionist: Yes, breakfast is served in the Garden Restaurant between seven and ten a.m.\nCaller: Perfect, thank you.',
 180),

(1, 'Library Membership Registration', 'Public Services', '/api/v1/listening/audio/part1b.mp3',
 'Librarian: Welcome to the City Central Library. Are you here to register?\nApplicant: Yes, I have just moved to the area and I would like to get a library card.\nLibrarian: Of course. I will need some details. Your full name, please?\nApplicant: Robert James Henderson.\nLibrarian: And your address?\nApplicant: Forty-seven Oak Street, Apartment B, Riverside.\nLibrarian: Postcode?\nApplicant: RV six, three PQ.\nLibrarian: Date of birth?\nApplicant: The twenty-third of September, nineteen ninety-five.\nLibrarian: Do you have a form of identification? A passport or driving licence?\nApplicant: I have my driving licence here.\nLibrarian: That is fine. Membership is free, and you can borrow up to eight books at a time for a period of three weeks.\nApplicant: Are there any fees for late returns?\nLibrarian: Yes, it is fifty pence per day per book. We also have an online catalogue where you can reserve books.\nApplicant: That sounds convenient. Thank you.',
 195);

-- Part 2: Monologue in everyday social context
INSERT INTO listening_parts (part_number, title, topic, audio_url, transcript_text, duration_seconds) VALUES
(2, 'Campus Tour Introduction', 'Education', '/api/v1/listening/audio/part2a.mp3',
 'Good morning, everyone, and welcome to Greenfield University. My name is Sarah Mitchell, and I will be your guide today. We will start with a quick overview of the campus facilities before walking through the main areas.\n\nFirst, the main library is located on the east side of campus, next to the Science Building. It is open from eight a.m. to midnight during term time. The library has recently been renovated and now includes a digital media lab on the third floor.\n\nThe Student Union building is at the centre of campus. This is where you will find the main cafeteria, which serves meals from seven thirty a.m. to eight p.m. There are also several clubs and societies based here. Registration for clubs takes place during the first two weeks of each semester.\n\nFor sports, we have an excellent Athletics Centre on the west side. It includes a twenty-five-metre swimming pool, a gymnasium, and six tennis courts. Students can purchase an annual membership for just seventy-five pounds, which is considerably less than commercial gyms in the area.\n\nThe Health Centre is located beside the Athletics Centre. It offers free consultations for all registered students. Appointments can be booked online or by telephoning the reception.\n\nNow, if you follow me, we will head towards the accommodation blocks.',
 240),

(2, 'Local Museum Tour Guide', 'Culture', '/api/v1/listening/audio/part2b.mp3',
 'Welcome to the Maritime Heritage Museum. Before we begin the tour, let me give you some practical information.\n\nThe museum was founded in eighteen seventy-six and moved to this building in nineteen fifty-two. We have over twelve thousand items in our collection, though only about three thousand are on display at any one time.\n\nWe are arranged over three floors. The ground floor covers the early history of seafaring, from ancient civilisations through to the medieval period. The first floor focuses on the age of exploration, including a full-scale replica of a sixteenth-century navigation room. The second floor houses our modern maritime gallery and a special exhibition space.\n\nThis month, our special exhibition is called Oceans Under Threat, which examines the impact of climate change on marine ecosystems. It runs until the thirtieth of November and there is no additional charge.\n\nPhotography is permitted in all galleries except the special exhibition area. Please do not use flash photography near the older artifacts.\n\nThe museum shop is on the ground floor near the exit. We also have a cafe on the first floor with views over the harbour. The cafe closes thirty minutes before the museum.\n\nGuided tours like this one run at eleven a.m. and two p.m. daily. Shall we begin?',
 255);

-- Part 3: Academic discussion
INSERT INTO listening_parts (part_number, title, topic, audio_url, transcript_text, duration_seconds) VALUES
(3, 'Research Project Discussion', 'Academic', '/api/v1/listening/audio/part3a.mp3',
 'Tutor: So, Hannah and Mark, let us discuss your research project on urban green spaces. How is the data collection going?\nHannah: We have completed the first phase. We surveyed two hundred residents in the Northside district about their use of local parks.\nMark: The response rate was actually higher than we expected, around sixty-eight percent.\nTutor: That is excellent. What were the preliminary findings?\nHannah: Well, the most significant finding was that seventy-three percent of respondents visit a green space at least once a week. However, only twelve percent rated the maintenance of their local park as good or excellent.\nMark: We also found a strong correlation between proximity to a park and reported levels of physical activity. People living within five hundred metres were twice as likely to exercise regularly.\nTutor: Interesting. And what about the qualitative data?\nHannah: We conducted fifteen in-depth interviews. A recurring theme was the desire for more community gardens. Several participants mentioned that growing their own vegetables would improve both their diet and their sense of community.\nTutor: How are you planning to present this in the final report?\nMark: We are thinking of combining statistical analysis with case studies from the interviews.\nTutor: Good approach. Make sure you also reference the Henderson Framework for evaluating urban wellbeing. It will strengthen your theoretical foundation.',
 270),

(3, 'Student Presentation Feedback', 'Academic', '/api/v1/listening/audio/part3b.mp3',
 'Dr. Chen: Right, Lucy and Tom, let us review your presentation on renewable energy adoption in developing countries. Overall, it was well structured.\nLucy: Thank you. We were worried about the time limit.\nDr. Chen: You managed it well. The introduction was concise and set up the argument effectively. However, I have a few suggestions. Tom, when you presented the statistics on solar panel installation costs, you mentioned a forty percent decrease over the past decade. Can you provide the source for that?\nTom: Yes, that was from the International Renewable Energy Agency report, published in twenty twenty-two.\nDr. Chen: Good. Make sure to include that citation in your written submission. Lucy, your analysis of the barriers to adoption was strong, particularly the point about infrastructure limitations in rural areas.\nLucy: We found that the biggest barrier was not the cost of technology itself but the lack of trained technicians to install and maintain the equipment.\nDr. Chen: That is a nuanced point and worth emphasising. One area for improvement would be your conclusion. It felt a bit rushed. Try to summarise the three main recommendations more clearly.\nTom: We will revise that section. Should we also add a comparison with fossil fuel subsidies?\nDr. Chen: Absolutely. That would add depth to your argument. The deadline for the written version is the twenty-eighth of November.',
 285);

-- Part 4: Academic monologue / lecture
INSERT INTO listening_parts (part_number, title, topic, audio_url, transcript_text, duration_seconds) VALUES
(4, 'Lecture on Sleep and Memory', 'Science', '/api/v1/listening/audio/part4a.mp3',
 'Today I want to talk about the relationship between sleep and memory consolidation, a topic that has generated considerable interest in neuroscience over the past two decades.\n\nMemory consolidation refers to the process by which newly acquired information is transformed from a fragile state into a more stable, long-term form. Research has consistently shown that sleep plays a critical role in this process.\n\nThere are broadly two types of memory that are affected. Declarative memory, which includes facts and events, and procedural memory, which involves skills and habits. Studies by Walker and Stickgold in two thousand four demonstrated that subjects who slept after learning a list of word pairs showed a twenty percent improvement in recall compared to those who remained awake for the same period.\n\nThe mechanism appears to involve the hippocampus and the neocortex. During slow-wave sleep, which occurs predominantly in the first half of the night, newly encoded memories are gradually transferred from the hippocampus to the neocortex for long-term storage. This process is sometimes called systems consolidation.\n\nREM sleep, on the other hand, seems particularly important for procedural memory and emotional processing. A study by Wagner and colleagues found that participants who were woken during REM sleep performed significantly worse on a motor sequence task than those allowed to complete their natural sleep cycles.\n\nThe implications for education are significant. Students who sacrifice sleep for additional study time may actually be undermining their ability to retain information.',
 300),

(4, 'Lecture on Urbanisation and Biodiversity', 'Environment', '/api/v1/listening/audio/part4b.mp3',
 'In this lecture, I want to examine how rapid urbanisation is affecting biodiversity, with particular reference to insect populations, which are often overlooked in discussions about conservation.\n\nGlobally, urban areas now cover approximately three percent of the Earth land surface, but this figure is projected to triple by twenty fifty. This expansion has profound consequences for local ecosystems.\n\nOne of the most well-documented impacts is habitat fragmentation. When a continuous area of natural habitat is divided by roads, buildings, and other infrastructure, the resulting fragments may be too small to support viable populations of certain species. A landmark study by Fahrig in two thousand three showed that fragmentation reduces species richness by an average of thirty percent.\n\nInsect populations have been particularly affected. A German study published in two thousand seventeen found that flying insect biomass had declined by seventy-six percent over twenty-seven years in protected nature reserves. While the exact causes are debated, urbanisation and associated light pollution are considered significant contributing factors.\n\nLight pollution disrupts nocturnal insects in several ways. Artificial lights attract insects away from their natural habitats, interfere with navigation, and disrupt mating behaviour. Research by Owens and Lewis in two thousand eighteen estimated that artificial light at night is responsible for the deaths of approximately one hundred billion insects annually in the United States alone.\n\nHowever, not all urban impacts are negative. Some cities have implemented green corridor programs that connect isolated patches of habitat, allowing species to move between them.',
 315);

-- =============================================
-- Seed Questions for each Part
-- =============================================

-- Part 1a: Hotel Reservation (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(1, 'FILL_BLANK', 'The caller wants to book a room for ___ nights.', 'three', 1),
(1, 'FILL_BLANK', 'The standard room costs ___ pounds per night.', 'eighty-five', 2),
(1, 'FILL_BLANK', 'The caller''s name is Catherine ___.', 'Williams', 3),
(1, 'FILL_BLANK', 'The booking reference number is ___.', 'HP-3352', 4),
(1, 'MCQ', 'Where is breakfast served?\nA) The Main Hall\nB) The Garden Restaurant\nC) Room Service\nD) The Lobby Cafe', 'B', 5);

-- Part 1b: Library Registration (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(2, 'FILL_BLANK', 'The applicant lives at ___ Oak Street.', 'Forty-seven', 1),
(2, 'FILL_BLANK', 'The postcode is ___.', 'RV6 3PQ', 2),
(2, 'FILL_BLANK', 'Members can borrow up to ___ books at a time.', 'eight', 3),
(2, 'FILL_BLANK', 'The loan period is ___ weeks.', 'three', 4),
(2, 'MCQ', 'What is the late return fee?\nA) Twenty pence per day\nB) Fifty pence per day per book\nC) One pound per week\nD) No fee', 'B', 5);

-- Part 2a: Campus Tour (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(3, 'MCQ', 'Where is the main library located?\nA) West side\nB) East side\nC) North side\nD) Centre of campus', 'B', 1),
(3, 'FILL_BLANK', 'The library has a digital media lab on the ___ floor.', 'third', 2),
(3, 'FILL_BLANK', 'The swimming pool is ___ metres long.', 'twenty-five', 3),
(3, 'FILL_BLANK', 'Annual sports membership costs ___ pounds.', 'seventy-five', 4),
(3, 'MCQ', 'How can students book Health Centre appointments?\nA) Only in person\nB) Online or by telephone\nC) Through the Student Union\nD) By email only', 'B', 5);

-- Part 2b: Museum Tour (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(4, 'FILL_BLANK', 'The museum was founded in ___.', '1876', 1),
(4, 'FILL_BLANK', 'The museum has over ___ items in its collection.', 'twelve thousand', 2),
(4, 'MCQ', 'What is on the first floor?\nA) Early history of seafaring\nB) Modern maritime gallery\nC) Age of exploration\nD) Special exhibition', 'C', 3),
(4, 'FILL_BLANK', 'The special exhibition is called ___.', 'Oceans Under Threat', 4),
(4, 'MCQ', 'Where is photography NOT permitted?\nA) Ground floor\nB) First floor\nC) Special exhibition area\nD) The cafe', 'C', 5);

-- Part 3a: Research Project (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(5, 'FILL_BLANK', 'The students surveyed ___ residents.', 'two hundred', 1),
(5, 'FILL_BLANK', 'The response rate was approximately ___ percent.', 'sixty-eight', 2),
(5, 'MCQ', 'What percentage of respondents visit green spaces weekly?\nA) 53%\nB) 68%\nC) 73%\nD) 85%', 'C', 3),
(5, 'FILL_BLANK', 'People living within ___ metres of a park were twice as likely to exercise.', 'five hundred', 4),
(5, 'MCQ', 'What framework did the tutor recommend?\nA) The Wilson Model\nB) The Henderson Framework\nC) The Green Spaces Index\nD) The Urban Health Guide', 'B', 5);

-- Part 3b: Presentation Feedback (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(6, 'FILL_BLANK', 'Solar panel installation costs decreased by ___ percent over the past decade.', 'forty', 1),
(6, 'FILL_BLANK', 'The statistics came from the International ___ Energy Agency.', 'Renewable', 2),
(6, 'MCQ', 'What was identified as the biggest barrier to adoption?\nA) Cost of technology\nB) Government regulations\nC) Lack of trained technicians\nD) Public awareness', 'C', 3),
(6, 'MCQ', 'Which section of the presentation needs improvement?\nA) Introduction\nB) Statistics\nC) Analysis\nD) Conclusion', 'D', 4),
(6, 'FILL_BLANK', 'The deadline for the written version is the ___ of November.', 'twenty-eighth', 5);

-- Part 4a: Sleep and Memory (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(7, 'FILL_BLANK', 'Memory consolidation transforms information from a fragile state into a more ___ form.', 'stable', 1),
(7, 'MCQ', 'What types of memory are discussed?\nA) Short-term and long-term\nB) Declarative and procedural\nC) Explicit and implicit\nD) Sensory and working', 'B', 2),
(7, 'FILL_BLANK', 'Subjects who slept showed a ___ percent improvement in recall.', 'twenty', 3),
(7, 'MCQ', 'During which sleep stage are memories transferred to the neocortex?\nA) REM sleep\nB) Light sleep\nC) Slow-wave sleep\nD) All stages equally', 'C', 4),
(7, 'FILL_BLANK', 'The process of transferring memories to long-term storage is called systems ___.', 'consolidation', 5);

-- Part 4b: Urbanisation and Biodiversity (5 questions)
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(8, 'FILL_BLANK', 'Urban areas currently cover approximately ___ percent of the Earth land surface.', 'three', 1),
(8, 'MCQ', 'By 2050, urban land coverage is projected to:\nA) Double\nB) Triple\nC) Quadruple\nD) Remain stable', 'B', 2),
(8, 'FILL_BLANK', 'Fragmentation reduces species richness by an average of ___ percent.', 'thirty', 3),
(8, 'FILL_BLANK', 'Flying insect biomass declined by ___ percent over 27 years.', 'seventy-six', 4),
(8, 'MCQ', 'What urban solution is mentioned for helping biodiversity?\nA) Reducing traffic\nB) Green corridor programs\nC) Banning pesticides\nD) Building taller buildings', 'B', 5);
