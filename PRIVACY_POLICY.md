# Privacy Policy - Phoenix Bot

**Effective Date**: August 14, 2026  
**Last Updated**: August 14, 2026  
**Version**: 1.0

---

## 1. Introduction

Phoenix Bot ("we," "us," "our," or "Bot") is committed to protecting your privacy. This Privacy
Policy explains how our Discord bot collects, uses, discloses, and safeguards your information when
you use the Bot in Discord servers.

Please read this Privacy Policy carefully. By using Phoenix Bot, you acknowledge that you have
read, understood, and agree to be bound by all the terms of this Privacy Policy.

---

## 2. Information We Collect

### 2.1 Information Automatically Collected

When you interact with Phoenix Bot, we automatically collect the following information:

- **User Identifiers**: Your Discord user ID (a unique numeric identifier)
- **Guild Information**: The Discord server ID where you interact with the Bot
- **Channel Information**: The Discord channel ID where messages are sent
- **Message Content**: The text of messages you send to the Bot or that mention the Bot
- **Timestamps**: The date and time when messages are sent
- **Display Information**: Your Discord username and server nickname
- **Command Usage**: Which commands you execute and their parameters

### 2.2 Information You Provide

- **Configuration Data**: Server settings and preferences you set for the Bot
- **Learning Data**: Phrases or responses you provide for the Bot to learn
- **Feedback Data**: Any upvotes, downvotes, or ratings you provide on Bot responses

### 2.3 Information We Do NOT Collect

- User email addresses (Discord users' email, not sent to Bot)
- Phone numbers
- Physical addresses
- Payment information
- IP addresses from command users
- Personal identification documents
- Health or biometric data

---

## 3. Legal Basis for Processing (GDPR)

We process your data based on the following legal grounds:

1. **Consent** (Article 6(1)(a) GDPR)
   - Implicit consent by using the Bot
   - Explicit consent for data export requests

2. **Legitimate Interests** (Article 6(1)(f) GDPR)
   - Bot operation and functionality
   - System security and fraud prevention
   - Service improvement and analytics
   - Compliance with legal obligations

3. **Performance of Contract** (Article 6(1)(b) GDPR)
   - Discord Terms of Service compliance
   - Providing Bot services as intended

4. **Legal Obligation** (Article 6(1)(c) GDPR)
   - Data retention for regulatory compliance
   - Response to legal process

---

## 4. How We Use Your Information

We use the information we collect for the following purposes:

### 4.1 Service Provision
- Process your commands
- Store conversation context
- Learn and improve Bot responses
- Provide personalized features

### 4.2 Service Improvement
- Analyze Bot usage patterns
- Identify and fix bugs
- Optimize performance
- Develop new features

### 4.3 Security & Safety
- Prevent abuse and spam
- Detect malicious activities
- Enforce rate limits
- Monitor for security threats
- Audit admin actions

### 4.4 Compliance
- Respond to data subject requests
- Comply with legal obligations
- Maintain audit trails
- Support incident response

### 4.5 We DO NOT Use Your Data For
- Selling to third parties
- Marketing or advertising
- Profiling for discrimination
- Automated decision-making about users
- Anything beyond Bot operation

---

## 5. Data Retention

We retain your data according to the following schedule:

| Data Type | Retention Period | Purpose |
|-----------|-----------------|---------|
| Conversation History | 60 days (configurable) | Context & learning |
| Learned Phrases | 60 days (configurable) | Bot responses |
| Audit Logs | 90 days | Security & compliance |
| Admin Actions | 90 days | Accountability |
| Backups | 30 days | Disaster recovery |
| User Data Requests | 3 years | Legal compliance |
| Error Logs | 30 days | Debugging |

After the retention period, data is automatically and permanently deleted from our systems.
Backups are encrypted and stored securely.

**You can request deletion anytime** using the `/gdpr-delete-my-data` command.

---

## 6. Data Sharing & Third Parties

### 6.1 Who We Share Data With

**Discord**
- Your Discord user ID and message content is shared with Discord to provide Bot services
- Governed by Discord's Privacy Policy: https://discord.com/privacy
- Discord is the data controller for platform data

**AWS (if cloud backups enabled)**
- Encrypted database backups may be stored in AWS S3
- Governed by AWS Privacy Policy: https://aws.amazon.com/privacy/
- Encryption keys remain in our possession

**Service Providers**
- Hosting providers (infrastructure only)
- Analytics tools (aggregate, non-identifying data only)
- Security testing services

### 6.2 Who We Do NOT Share Data With

- Marketing or advertising companies
- Data brokers or data resellers
- Social media companies
- Government or law enforcement (except by legal order)
- Any third parties for commercial purposes

### 6.3 Legal Process

We may disclose your data if required by law, including:
- Valid court orders
- Subpoenas or warrants
- Legal investigations
- Terms of Service violations

We will attempt to notify you of such requests unless legally prohibited from doing so.

---

## 7. Your Rights

You have the following rights regarding your data:

### 7.1 Right to Access (DSAR)
**Request**: `/gdpr-export-my-data`  
**Response Time**: Within 24 hours  
**Format**: JSON file containing all your data

### 7.2 Right to Rectification
Contact us to correct inaccurate information about you.

### 7.3 Right to Erasure (Right to Be Forgotten)
**Request**: `/gdpr-delete-my-data`  
**Action**: All your data permanently deleted  
**Timeframe**: Immediate deletion from main database, 30 days from backups

### 7.4 Right to Restrict Processing
Contact us to restrict how your data is used (e.g., disable learning).

### 7.5 Right to Data Portability
We provide your data in machine-readable JSON format for export to other services.

### 7.6 Right to Object
You can object to:
- Automated processing
- Marketing uses
- Profiling (if applicable)

### 7.7 Rights Related to Automated Decision-Making
We do not make automated decisions that significantly affect your rights. All admin actions are
logged and reviewable.

---

## 8. Security Measures

### 8.1 Encryption
- **At Rest**: AES-256 encryption using SQLCipher
- **In Transit**: TLS/HTTPS for all communications
- **Encryption Keys**: Stored separately from data, never in code

### 8.2 Access Controls
- Role-based access (Owner > Admin > Moderator > User)
- Multi-step confirmation for sensitive operations
- Audit logging of all admin actions
- Token masking in logs

### 8.3 Security Testing
- Regular penetration testing
- Vulnerability scanning
- Input validation against injection attacks
- Rate limiting to prevent abuse

### 8.4 Infrastructure Security
- Secure key management
- Write-ahead logging (WAL) for database consistency
- Regular backups with integrity verification
- Incident response procedures

---

## 9. Children's Privacy (COPPA)

Phoenix Bot is not intended for children under 13 years old. We do not knowingly collect personal
data from children under 13. Users must be at least 13 years old to comply with Discord's Terms 
of Service.

If we become aware that we have collected data from a child under 13, we will delete such data 
immediately and terminate the child's access to the Bot.

---

## 10. International Data Transfers

If you are located outside the United States, your data may be transferred to and processed in the 
United States and other countries. These countries may have different data protection laws 
than your home country.

When data is transferred internationally, we implement:
- Standard Contractual Clauses (SCCs) under GDPR
- Appropriate safeguards and protections
- Compliance with applicable data transfer laws

By using Phoenix Bot, you consent to the transfer of your information to countries outside your 
country of residence.

---

## 11. Automated Decision-Making & Profiling

### 11.1 Learning System
The Bot learns from your messages to improve responses. This learning:
- Does NOT create profiles about you
- Does NOT make decisions affecting your rights
- Can be disabled via bot configuration
- Does NOT involve cross-server tracking

### 11.2 Audit Trails
All admin actions are logged but:
- Do NOT involve automated decisions
- Do NOT discriminate based on protected characteristics
- Are subject to human review
- Can be appealed or overridden

---

## 12. Data Breach Notification

In the event of a confirmed data breach affecting your personal data, we will:

1. **Notify you within 72 hours** of discovery (if required by law)
2. **Provide details** about what data was compromised
3. **Explain the impact** and what we're doing about it
4. **Offer support** including free credit monitoring (if applicable)
5. **Contact authorities** as required by law

You will be notified via:
- Discord direct message (if possible)
- Guild announcements in affected servers
- Public website notice

---

## 13. Policy Changes

We may update this Privacy Policy from time to time. Material changes will be announced:
- At least 30 days in advance
- Via Bot announcement in Discord
- Via website/documentation updates
- Via direct notification if legally required

Your continued use of Phoenix Bot after changes constitutes acceptance of the 
updated Privacy Policy. We encourage you to review this policy periodically to stay informed.

---

## 14. Contact Information

### For Privacy Concerns
**Email**: privacy@phoenix-bot.dev  
**Response Time**: 30 days  
**Mailing Address**: [YOUR ADDRESS]

### For Data Subject Requests
- **Access/Export**: `/gdpr-export-my-data`
- **Deletion**: `/gdpr-delete-my-data`
- **Other Requests**: Email privacy@phoenix-bot.dev

### Data Protection Authority
If you have concerns about our privacy practices, you may file a complaint with your local data 
protection authority:

- **EU**: Local supervisory authority (https://edpb.ec.europa.eu/about-edpb/board/members_en)
- **UK**: Information Commissioner's Office (ICO) - https://ico.org.uk
- **California**: California Privacy Protection Agency (CPPA) - https://cppa.ca.gov
- **Other Regions**: Local privacy regulator

---

## 15. Additional Information

### California Privacy Rights (CCPA/CPRA)
California residents have additional privacy rights:
- Right to know what data is collected
- Right to delete personal information
- Right to opt-out of data sales (we don't sell data)
- Right to limit use and disclosure
- Right to non-discrimination for exercising rights

### Virginia Privacy Rights (VAMSA)
- Right to access and delete personal data
- Right to correct inaccurate data
- Right to data portability
- Right to opt-out of targeted advertising (not applicable - we don't advertise)

---

## 16. Definitions

- **Personal Data**: Any information relating to an identified or identifiable person
- **Processing**: Any operation performed on data (collection, storage, use, etc.)
- **Data Controller**: Entity determining purposes and means of processing (Phoenix Bot Project)
- **Data Processor**: Entity processing data on behalf of the controller (Discord, AWS, etc.)
- **Data Subject**: The person whose data is processed (you)

---

## 17. Acknowledgments

- Discord is a trademark of Discord Inc.
- Star Citizen is a trademark of Cloud Imperium Games
- Phoenix Bot is an independent project not affiliated with either

---

**Phoenix Bot Privacy Policy**

Document Version: 1.0  
Effective Date: August 14, 2026  
Last Updated: August 14, 2026  
Next Review: August 14, 2027

For questions or concerns, contact: privacy@phoenix-bot.dev

Thank you for using Phoenix Bot!
